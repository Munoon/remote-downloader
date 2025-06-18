package io.remotedownloader.dao;

import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.StorageModel;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FilesStorageDao {
    private final ConcurrentMap<String, DownloadingFile> downloadingFiles; // id -> file
    private final ConcurrentMap<String, DownloadingFile[]> userFiles; // owner username -> files[]
    private final StorageDao storageDao;

    public FilesStorageDao(StorageDao storageDao) {
        this.storageDao = storageDao;

        this.downloadingFiles = new ConcurrentHashMap<>();
        this.userFiles = new ConcurrentHashMap<>();

        Map<String, DownloadingFile> files = storageDao.readAllRecords(StorageModel.DOWNLOADING_FILE);
        for (DownloadingFile value : files.values()) {
            addFileWithoutStoring(value);
        }
    }

    public DownloadingFile[] getUserFiles(String ownerUsername) {
        return userFiles.getOrDefault(ownerUsername, DownloadingFile.EMPTY_ARRAY);
    }

    public DownloadingFile getById(String id) {
        return downloadingFiles.get(id);
    }

    public void updateFile(DownloadingFile file) {
        String fileId = file.id;
        downloadingFiles.put(fileId, file);
        userFiles.compute(file.ownerUsername, (username, files) -> {
            if (files == null) {
                return new DownloadingFile[]{file};
            }

            for (int i = 0; i < files.length; i++) {
                if (files[i].id.equals(fileId)) {
                    DownloadingFile[] updatedFiles = Arrays.copyOf(files, files.length);
                    updatedFiles[i] = file;
                    return updatedFiles;
                }
            }

            DownloadingFile[] updatedFiles = new DownloadingFile[files.length + 1];
            System.arraycopy(files, 0, updatedFiles, 0, files.length);
            updatedFiles[files.length] = file;
            return updatedFiles;
        });

        storageDao.saveRecord(file);
    }

    public void addFile(DownloadingFile file) {
        addFileWithoutStoring(file);
        storageDao.saveRecord(file);
    }

    private void addFileWithoutStoring(DownloadingFile file) {
        String fileId = file.id;
        downloadingFiles.put(fileId, file);
        userFiles.compute(file.ownerUsername, (username, files) -> {
            if (files == null) {
                return new DownloadingFile[]{file};
            }

            DownloadingFile[] updatedFiles = new DownloadingFile[files.length + 1];
            System.arraycopy(files, 0, updatedFiles, 0, files.length);
            updatedFiles[files.length] = file;
            return updatedFiles;
        });
    }

    public void deleteById(DownloadingFile file) {
        String fileId = file.id;
        downloadingFiles.remove(fileId);
        userFiles.compute(file.ownerUsername, (username, files) -> {
            if (files == null) {
                return null;
            }

            for (int i = 0; i < files.length; i++) {
                if (files[i].id.equals(fileId)) {
                    DownloadingFile[] newFiles = new DownloadingFile[files.length - 1];
                    System.arraycopy(files, 0, newFiles, 0, i);
                    System.arraycopy(files, i + 1, newFiles, i, files.length - i - 1);
                    return newFiles;
                }
            }
            return files;
        });

        storageDao.deleteRecord(StorageModel.DOWNLOADING_FILE, fileId);
    }

    public Collection<DownloadingFile> getAllFiles() {
        return downloadingFiles.values();
    }

    public void clear() {
        downloadingFiles.clear();
        userFiles.clear();
    }
}
