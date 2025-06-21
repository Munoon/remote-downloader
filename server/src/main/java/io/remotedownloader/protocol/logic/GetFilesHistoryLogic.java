package io.remotedownloader.protocol.logic;

import io.remotedownloader.Holder;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.GetFilesHistoryRequestDTO;
import io.remotedownloader.model.dto.Page;
import io.remotedownloader.protocol.StringMessage;

import java.util.Arrays;
import java.util.Comparator;

public class GetFilesHistoryLogic {
    private final FilesStorageDao filesStorageDao;

    public GetFilesHistoryLogic(Holder holder) {
        this.filesStorageDao = holder.filesStorageDao;
    }

    public StringMessage handleRequest(StringMessage msg, String username) {
        GetFilesHistoryRequestDTO req = msg.parseJsonAndValidate(GetFilesHistoryRequestDTO.class);

        DownloadingFile[] files = filesStorageDao.getUserFiles(username);

        DownloadingFile[] sortedFiles = Arrays.copyOf(files, files.length);
        Arrays.sort(sortedFiles, Comparator.<DownloadingFile>comparingLong(f -> f.createdAt).reversed());

        int size = req.size();
        int offset = req.offset();
        int pageSize = sortedFiles.length >= offset + size ? size : sortedFiles.length - offset;

        DownloadFileDTO[] response = new DownloadFileDTO[pageSize];
        for (int i = 0; i < pageSize; i++) {
            response[i] = new DownloadFileDTO(sortedFiles[offset + i]);
        }
        return StringMessage.json(msg, new Page<>(response, sortedFiles.length));
    }
}
