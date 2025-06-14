package io.remotedownloader.protocol.logic;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.FileIdRequestDTO;
import io.remotedownloader.protocol.StringMessage;

public class ResumeDownloadLogic {
    private final FilesStorageDao filesStorageDao;
    private final DownloadManagerDao downloadManagerDao;

    public ResumeDownloadLogic(Holder holder) {
        this.filesStorageDao = holder.filesStorageDao;
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    public StringMessage handleRequest(ChannelHandlerContext ctx, StringMessage req, String username) {
        String fileId = req.parseJson(FileIdRequestDTO.class).fileId();

        DownloadingFile file = filesStorageDao.getById(fileId);
        if (file == null || !username.equals(file.ownerUsername)) {
            return StringMessage.error(req, Error.ErrorTypes.NOT_FOUND, "File is not found.");
        }
        if (file.status != DownloadingFileStatus.PAUSED) {
            return StringMessage.error(req, Error.ErrorTypes.FAILED_TO_DOWNLOAD, "File status should be 'Paused'.");
        }

        downloadManagerDao.resumeDownloading(ctx, req, file);
        return null;
    }
}
