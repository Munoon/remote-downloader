package io.remotedownloader.dao;

import io.remotedownloader.model.DownloadingFile;

import java.util.Arrays;
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
    }

    public void addFile(DownloadingFile file) {
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
    }
}
