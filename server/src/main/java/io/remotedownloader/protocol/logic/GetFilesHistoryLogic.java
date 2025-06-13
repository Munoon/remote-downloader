package io.remotedownloader.protocol.logic;

import io.remotedownloader.Holder;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.Page;
import io.remotedownloader.protocol.StringMessage;

public class GetFilesHistoryLogic {
    private final FilesStorageDao filesStorageDao;

    public GetFilesHistoryLogic(Holder holder) {
        this.filesStorageDao = holder.filesStorageDao;
    }

    public StringMessage handleRequest(StringMessage req, String username) {
        DownloadingFile[] files = filesStorageDao.getUserFiles(username);
        DownloadFileDTO[] response = new DownloadFileDTO[files.length];
        for (int i = 0; i < files.length; i++) {
            response[i] = new DownloadFileDTO(files[i]);
        }
        return StringMessage.json(req, new Page<>(response, response.length));
    }
}
