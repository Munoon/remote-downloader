package io.remotedownloader.protocol.logic;

import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.FileIdRequestDTO;
import io.remotedownloader.protocol.StringMessage;

public class DeleteFileLogic {
    private final FilesStorageDao filesStorageDao;
    private final DownloadManagerDao downloadManagerDao;

    public DeleteFileLogic(Holder holder) {
        this.filesStorageDao = holder.filesStorageDao;
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    public StringMessage handleRequest(StringMessage req, String username) {
        String fileId = req.parseJson(FileIdRequestDTO.class).fileId();

        DownloadingFile file = filesStorageDao.getById(fileId);
        if (file == null || !username.equals(file.ownerUsername)) {
            return StringMessage.error(req, Error.ErrorTypes.NOT_FOUND, "File is not found.");
        }

        downloadManagerDao.stopDownloading(file.id);
        filesStorageDao.deleteById(file);
        downloadManagerDao.deleteFile(file);

        return StringMessage.ok(req);
    }
}
