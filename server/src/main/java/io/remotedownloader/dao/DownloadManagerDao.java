package io.remotedownloader.dao;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.remotedownloader.ServerProperties;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.ListFileDTO;
import io.remotedownloader.model.dto.ListFoldersResponseDTO;
import io.remotedownloader.protocol.ErrorException;
import io.remotedownloader.protocol.StringMessage;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class DownloadManagerDao {
    private static final Logger log = LogManager.getLogger(DownloadManagerDao.class);
    private final ServerProperties properties;
    private final AsyncHttpClient asyncHttpClient;

    public DownloadManagerDao(ServerProperties properties, AsyncHttpClient asyncHttpClient) {
        this.properties = properties;
        this.asyncHttpClient = asyncHttpClient;
    }

    public void download(ChannelHandlerContext ctx, StringMessage req, String url, String fileName, String path) {
        Path filePath = resolveFilePath(path, fileName);
        SeekableByteChannel fileChannel = createFileChannel(filePath);

        asyncHttpClient.prepareGet(url)
                .setRequestTimeout(Duration.ofSeconds(5))
                .execute(new AsyncHandler<>() {
                    @Override
                    public State onStatusReceived(HttpResponseStatus responseStatus) {
                        int statusCode = responseStatus.getStatusCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            log.info("Start downloading '{}' to '{}'", url, filePath);
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
                        long contentLength;
                        try {
                            String contentLengthValue = headers.get(HttpHeaderNames.CONTENT_LENGTH);
                            contentLength = Long.parseLong(contentLengthValue);
                        } catch (Exception e) {
                            log.warn("Failed to get file content length", e);
                            contentLength = -1;
                        }

                        DownloadFileDTO file = new DownloadFileDTO(
                                UUID.randomUUID().toString(),
                                fileName,
                                DownloadingFileStatus.DOWNLOADING,
                                contentLength,
                                0, 0);
                        ctx.writeAndFlush(StringMessage.json(req, file));

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

    public ListFoldersResponseDTO listFolders(String path) {
        try {
            Path downloadFolder = resolveDownloadFolder();
            if (path != null) {
                downloadFolder = downloadFolder.resolve(path);
            }

            try (Stream<Path> files = Files.list(downloadFolder)) {
                List<ListFileDTO> result = files
                        .map(file -> new ListFileDTO(Files.isDirectory(file), file.getFileName().toString()))
                        .toList();
                return new ListFoldersResponseDTO(true, result);
            }
        } catch (Exception e) {
            log.warn("Failed to list files in download folder", e);
            return new ListFoldersResponseDTO(false, Collections.emptyList());
        }
    }

    private static SeekableByteChannel createFileChannel(Path filePath) {
        try {
            return Files.newByteChannel(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception e) {
            log.warn("Failed to create a file '{}'", filePath, e);
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a file on a server.");
        }
    }

    private Path resolveFilePath(String path, String fileName) {
        Path downloadFolder = resolveDownloadFolder();
        if (path != null) {
            downloadFolder = downloadFolder.resolve(path);
        }
        return downloadFolder.resolve(fileName);
    }

    private Path resolveDownloadFolder() {
        Path downloadFolder = Path.of(properties.getDownloadFolder());
        if (!Files.exists(downloadFolder)) {
            try {
                Files.createDirectory(downloadFolder);
            } catch (Exception e) {
                log.warn("Failed to create a download folder '{}'", downloadFolder, e);
                throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a download folder.");
            }
        } else if (!Files.isDirectory(downloadFolder)) {
            log.warn("The download folder is actually file.");
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Download folder is a file.");
        }
        return downloadFolder;
    }
}
