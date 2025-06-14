package io.remotedownloader.downloader;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.protocol.StringMessage;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

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
            filesStorageDao.updateFile(file.withStatus(DownloadingFileStatus.ERROR));
        }
    }

    @Override
    protected void onStartDownloading(long fileLength) {
        if (file.status != DownloadingFileStatus.DOWNLOADING || file.totalBytes != fileLength) {
            this.file = new DownloadingFile(
                    file.id,
                    file.name,
                    file.path,
                    file.url,
                    file.ownerUsername,
                    DownloadingFileStatus.DOWNLOADING,
                    fileLength,
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
}
