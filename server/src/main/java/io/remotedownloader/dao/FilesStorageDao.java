package io.remotedownloader.dao;

import io.remotedownloader.model.DownloadingFile;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FilesStorageDao {
    // id -> file
    private final ConcurrentMap<String, DownloadingFile> downloadingFiles = new ConcurrentHashMap<>();

    // owner username -> files[]
    private final ConcurrentMap<String, DownloadingFile[]> userFiles = new ConcurrentHashMap<>();

    public DownloadingFile[] getUserFiles(String ownerUsername) {
        return userFiles.getOrDefault(ownerUsername, DownloadingFile.EMPTY_ARRAY);
    }

    public DownloadingFile getById(String id) {
        return downloadingFiles.get(id);
    }

    public void saveFile(DownloadingFile file) {
        downloadingFiles.put(file.id(), file);
        // TODO update user files
    }

    public void deleteById(DownloadingFile file) {
        downloadingFiles.remove(file.id());
        // TODO remove from user fles
    }
}
