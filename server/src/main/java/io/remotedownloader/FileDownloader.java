package io.remotedownloader;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.protocol.StringMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

public class FileDownloader implements AsyncHandler<Object> {
    private static final Logger log = LogManager.getLogger(FileDownloader.class);
    private final ChannelHandlerContext ctx;
    private final StringMessage msg;
    private final String fileId;
    private final String ownerUsername;
    private final DownloadUrlRequestDTO req;
    private final Path filePath;
    private final SeekableByteChannel fileChannel;
    private final FilesStorageDao filesStorageDao;

    private DownloadingFile file;

    public FileDownloader(ChannelHandlerContext ctx,
                          StringMessage msg,
                          String fileId,
                          String ownerUsername,
                          DownloadUrlRequestDTO req,
                          Path filePath,
                          SeekableByteChannel fileChannel,
                          FilesStorageDao filesStorageDao) {
        this.ctx = ctx;
        this.msg = msg;
        this.fileId = fileId;
        this.ownerUsername = ownerUsername;
        this.req = req;
        this.filePath = filePath;
        this.fileChannel = fileChannel;
        this.filesStorageDao = filesStorageDao;
    }

    @Override
    public State onStatusReceived(HttpResponseStatus responseStatus) {
        int statusCode = responseStatus.getStatusCode();
        if (statusCode >= 200 && statusCode < 300) {
            log.info("Start downloading '{}' to '{}'", req.url(), filePath);
            return State.CONTINUE;
        } else {
            log.info("Received {} response code from server when trying to download {}. Aborting...",
                    statusCode, req.url());
            StringMessage response = StringMessage.error(
                    msg,
                    Error.ErrorTypes.FAILED_TO_DOWNLOAD,
                    "Server respond with an error.");
            ctx.writeAndFlush(response);
            return State.ABORT;
        }
    }

    @Override
    public State onHeadersReceived(HttpHeaders headers) {
        DownloadingFile file = new DownloadingFile(
                fileId,
                req.fileName(),
                req.path(),
                ownerUsername,
                DownloadingFileStatus.DOWNLOADING,
                getContentLength(headers),
                0, 0);
        this.file = file;

        filesStorageDao.saveFile(file);
        ctx.writeAndFlush(StringMessage.json(msg, new DownloadFileDTO(file)));
        return State.CONTINUE;
    }

    @Override
    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
        try {
            ByteBuffer byteBuffer = bodyPart.getBodyByteBuffer();
            long size = byteBuffer.remaining();
            fileChannel.write(byteBuffer);

            if (file != null) {
                // as long, as we are writing to this field only from a single thread - this is fine
                //noinspection NonAtomicOperationOnVolatileField
                file.downloadedBytes += size;
            }
        } catch (IOException e) {
            log.warn("Failed to write to a file {}", filePath);
            return State.ABORT;
        }
        return State.CONTINUE;
    }

    @Override
    public void onThrowable(Throwable t) {
        log.warn("Failed to download '{}'", req.url(), t);
        finishDownloading(DownloadingFileStatus.ERROR);
    }

    @Override
    public Object onCompleted() {
        log.info("File '{}' has been downloaded", filePath);
        finishDownloading(DownloadingFileStatus.DOWNLOADED);
        return null;
    }

    private void finishDownloading(DownloadingFileStatus status) {
        try {
            fileChannel.close();
        } catch (IOException e) {
            log.warn("Failed to close a file", e);
        }
        if (file != null) {
            filesStorageDao.saveFile(file.withStatus(status));
        }
    }

    private static long getContentLength(HttpHeaders headers) {
        long contentLength;
        try {
            String contentLengthValue = headers.get(HttpHeaderNames.CONTENT_LENGTH);
            contentLength = Long.parseLong(contentLengthValue);
        } catch (Exception e) {
            log.warn("Failed to get file content length", e);
            contentLength = -1;
        }
        return contentLength;
    }
}
