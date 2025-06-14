package io.remotedownloader.downloader;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.protocol.StringMessage;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

public class NewFileDownloader extends BaseFileDownloader {
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
                             SeekableByteChannel fileChannel,
                             FilesStorageDao filesStorageDao) {
        super(req.url(), filePath, fileChannel, filesStorageDao);
        this.ctx = ctx;
        this.msg = msg;
        this.fileId = fileId;
        this.ownerUsername = ownerUsername;
        this.req = req;
    }

    @Override
    protected void onStartFailure() {
        StringMessage response = StringMessage.error(
                msg,
                Error.ErrorTypes.FAILED_TO_DOWNLOAD,
                "Server respond with an error.");
        ctx.writeAndFlush(response);
    }

    @Override
    protected void onStartDownloading(long fileLength) {
        long now = System.currentTimeMillis();
        DownloadingFile file = new DownloadingFile(
                fileId,
                req.fileName(),
                req.path(),
                req.url(),
                ownerUsername,
                DownloadingFileStatus.DOWNLOADING,
                fileLength,
                now,
                now
        );
        this.file = file;

        filesStorageDao.addFile(file);
        ctx.writeAndFlush(StringMessage.json(msg, new DownloadFileDTO(file)));
    }
}
