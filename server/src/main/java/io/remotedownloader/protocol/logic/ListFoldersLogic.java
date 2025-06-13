package io.remotedownloader.protocol.logic;

import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.model.dto.ListFoldersRequestDTO;
import io.remotedownloader.model.dto.ListFoldersResponseDTO;
import io.remotedownloader.protocol.StringMessage;

public class ListFoldersLogic {
    private final DownloadManagerDao downloadManagerDao;

    public ListFoldersLogic(Holder holder) {
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    public StringMessage handleRequest(StringMessage msg) {
        ListFoldersRequestDTO req = msg.parseJson(ListFoldersRequestDTO.class);
        ListFoldersResponseDTO response = downloadManagerDao.listFolders(req.path());
        return StringMessage.json(msg, response);
    }
}
