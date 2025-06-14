package io.remotedownloader.model;

public enum StorageModel {
    USER(User.class),
    DOWNLOADING_FILE(DownloadingFile.class);

    public final Class<?> clazz;

    StorageModel(Class<?> clazz) {
        this.clazz = clazz;
    }
}
