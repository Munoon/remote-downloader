package io.remotedownloader.dao;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.remotedownloader.ServerProperties;
import io.remotedownloader.protocol.ErrorException;
import io.remotedownloader.protocol.StringMessage;
import io.remotedownloader.protocol.dto.Error;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

public class DownloadManagerDao {
    private static final Logger log = LogManager.getLogger(DownloadManagerDao.class);
    private final ServerProperties properties;
    private final AsyncHttpClient asyncHttpClient;

    public DownloadManagerDao(ServerProperties properties, AsyncHttpClient asyncHttpClient) {
        this.properties = properties;
        this.asyncHttpClient = asyncHttpClient;
    }

    public void download(ChannelHandlerContext ctx, StringMessage req, String url, String fileName) {
        Path filePath = resolveFilePath(fileName);
        SeekableByteChannel fileChannel = createFileChannel(filePath);

        asyncHttpClient.prepareGet(url)
                .setRequestTimeout(Duration.ofSeconds(5))
                .execute(new AsyncHandler<>() {
                    @Override
                    public State onStatusReceived(HttpResponseStatus responseStatus) {
                        int statusCode = responseStatus.getStatusCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            log.info("Start downloading '{}' to '{}'", url, filePath);
                            ctx.writeAndFlush(StringMessage.ok(req));
                            return State.CONTINUE;
                        } else {
                            log.info("Received {} response code from server when trying to download {}. Aborting...",
                                    statusCode, url);
                            StringMessage response = StringMessage.error(
                                    req,
                                    Error.ErrorTypes.FAILED_TO_DOWNLOAD,
                                    "Server respond with an error.");
                            ctx.writeAndFlush(response);
                            return State.ABORT;
                        }
                    }

                    @Override
                    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
                        try {
                            fileChannel.write(bodyPart.getBodyByteBuffer());
                        } catch (IOException e) {
                            log.warn("Failed to write to a file {}", filePath);
                            return State.ABORT;
                        }
                        return State.CONTINUE;
                    }

                    @Override
                    public State onHeadersReceived(HttpHeaders headers) {
                        return State.CONTINUE;
                    }

                    @Override
                    public Object onCompleted() throws Exception {
                        log.info("File '{}' has been downloaded", filePath);
                        fileChannel.close();
                        return null;
                    }

                    @Override
                    public void onThrowable(Throwable t) {
                        log.warn("Failed to download '{}'", url, t);
                        try {
                            fileChannel.close();
                        } catch (Exception e) {
                            log.warn("Failed to close a file", e);
                        }
                    }
                });
    }

    private static SeekableByteChannel createFileChannel(Path filePath) {
        try {
            return Files.newByteChannel(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception e) {
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a file on a server.");
        }
    }

    private Path resolveFilePath(String fileName) {
        Path downloadFolder = Path.of(properties.getDownloadFolder());
        if (!Files.exists(downloadFolder)) {
            try {
                Files.createDirectory(downloadFolder);
            } catch (Exception e) {
                throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a download folder.");
            }
        } else if (!Files.isDirectory(downloadFolder)) {
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Download folder is a file.");
        }

        return downloadFolder.resolve(fileName);
    }
}
