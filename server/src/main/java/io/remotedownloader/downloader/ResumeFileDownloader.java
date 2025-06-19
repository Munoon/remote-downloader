package io.remotedownloader.downloader;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.protocol.StringMessage;
import org.asynchttpclient.HttpResponseStatus;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

import static io.netty.handler.codec.http.HttpResponseStatus.PARTIAL_CONTENT;

public class ResumeFileDownloader extends BaseFileDownloader {
    private final ChannelHandlerContext ctx;
    private final StringMessage msg;

    public ResumeFileDownloader(ChannelHandlerContext ctx,
                                StringMessage msg,
                                DownloadingFile file,
                                Path filePath,
                                SeekableByteChannel fileChannel,
                                FilesStorageDao filesStorageDao) {
        super(file.url, filePath, fileChannel, filesStorageDao);
        this.file = file;
        this.ctx = ctx;
        this.msg = msg;
    }

    @Override
    protected void onStartFailure() {
        if (ctx != null) {
            StringMessage response = StringMessage.error(
                    msg,
                    Error.ErrorTypes.FAILED_TO_DOWNLOAD,
                    "Server respond with an error.");
            ctx.writeAndFlush(response);
        } else {
            // if ctx == null -> this means, we are resuming downloading files on boot
            markFile(DownloadingFileStatus.ERROR);
        }
    }

    @Override
    protected void onStartDownloading(HttpResponseStatus status, HttpHeaders headers) {
        long fileLength = getFileLength(status, headers);
        if (file.status != DownloadingFileStatus.DOWNLOADING
            || file.speedBytesPerMS != 0
            || (fileLength != -1 && file.totalBytes != fileLength)) {
            this.file = new DownloadingFile(
                    file.id,
                    file.name,
                    file.path,
                    file.url,
                    file.ownerUsername,
                    DownloadingFileStatus.DOWNLOADING,
                    fileLength != -1 ? fileLength : file.totalBytes,
                    file.createdAt,
                    System.currentTimeMillis(),
                    file.downloadedBytes,
                    0
            );

            filesStorageDao.updateFile(file);
        }

        if (ctx != null) {
            ctx.writeAndFlush(StringMessage.json(msg, new DownloadFileDTO(file)));
        }
    }

    @Override
    protected void onError() {
        onStartFailure();
    }

    private static long getFileLength(HttpResponseStatus status, HttpHeaders headers) {
        if (status.getStatusCode() == PARTIAL_CONTENT.code()) {
            String contentRange = headers.get(HttpHeaderNames.CONTENT_RANGE);
            if (contentRange != null) {
                int fileLengthSeparator = contentRange.lastIndexOf('/');
                int length = contentRange.length();
                if (fileLengthSeparator != -1 && length > fileLengthSeparator + 1) {
                    try {
                        return Long.parseLong(contentRange, fileLengthSeparator + 1, length, 10);
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        } else {
            String contentLengthValue = headers.get(HttpHeaderNames.CONTENT_LENGTH);
            if (contentLengthValue != null) {
                try {
                    return Long.parseLong(contentLengthValue);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }

        return -1;
    }
}
