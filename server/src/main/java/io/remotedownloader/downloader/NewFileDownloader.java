package io.remotedownloader.downloader;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.remotedownloader.ServerProperties;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.protocol.StringMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.HttpResponseStatus;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class NewFileDownloader extends BaseFileDownloader {
    private static final Logger log = LogManager.getLogger(NewFileDownloader.class);
    private final ChannelHandlerContext ctx;
    private final StringMessage msg;
    private final String fileId;
    private final String ownerUsername;
    private final DownloadUrlRequestDTO req;

    public NewFileDownloader(ChannelHandlerContext ctx,
                             StringMessage msg,
                             String fileId,
                             String ownerUsername,
                             DownloadUrlRequestDTO req,
                             Path filePath,
                             FilesStorageDao filesStorageDao,
                             ServerProperties serverProperties) {
        super(req.url(), filePath, filesStorageDao, serverProperties);
        this.ctx = ctx;
        this.msg = msg;
        this.fileId = fileId;
        this.ownerUsername = ownerUsername;
        this.req = req;
    }

    @Override
    protected void onStartFailure() {
        sendErrorMessage();
        try {
            // ideally, we should do this in a non-Netty thread,
            // but this is a rare operation, so it's not critical
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("Failed to delete the file '{}' after failing to download it", filePath, e);
        }
    }

    @Override
    protected boolean onStartDownloading(HttpResponseStatus status, HttpHeaders headers) {
        long contentLength = getContentLength(headers);

        try {
            this.randomAccessFile = new RandomAccessFile(filePath.toFile(), "rw");
            if (contentLength > 0) {
                randomAccessFile.setLength(contentLength);
            }
            this.fileChannel = randomAccessFile.getChannel();
        } catch (Exception e) {
            log.warn("Failed to open file {}", filePath, e);

            StringMessage response = StringMessage.error(
                    msg,
                    Error.ErrorTypes.FAILED_TO_DOWNLOAD,
                    "Failed to start loading.");
            ctx.writeAndFlush(response);
            return false;
        }

        long now = System.currentTimeMillis();
        DownloadingFile file = new DownloadingFile(
                fileId,
                req.fileName(),
                req.path(),
                req.url(),
                ownerUsername,
                DownloadingFileStatus.DOWNLOADING,
                contentLength > 0 ? contentLength : -1,
                0,
                now,
                now
        );
        this.file = file;

        filesStorageDao.addFile(file);
        ctx.writeAndFlush(StringMessage.json(msg, new DownloadFileDTO(file)));
        return true;
    }

    @Override
    protected void onError() {
        if (file != null) {
            markFile(DownloadingFileStatus.ERROR);
        } else {
            sendErrorMessage();
        }
    }

    private void sendErrorMessage() {
        StringMessage response = StringMessage.error(
                msg,
                Error.ErrorTypes.FAILED_TO_DOWNLOAD,
                "Server respond with an error.");
        ctx.writeAndFlush(response);
    }

    private static long getContentLength(HttpHeaders headers) {
        try {
            String contentLengthValue = headers.get(HttpHeaderNames.CONTENT_LENGTH);
            if (contentLengthValue != null) {
                return Long.parseLong(contentLengthValue);
            }
        } catch (Exception e) {
            log.warn("Failed to get file content length", e);
        }
        return -1;
    }
}
