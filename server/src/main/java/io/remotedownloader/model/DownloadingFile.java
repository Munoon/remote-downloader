package io.remotedownloader.model;

public record DownloadingFile(
        String id,
        String name,
        String ownerUsername,
        DownloadingFileStatus status,
        long totalBytes,
        long downloadedBytes,
        long speedBytesPerSecond
) {
    public static final DownloadingFile[] EMPTY_ARRAY = new DownloadingFile[0];

    public DownloadingFile withStatus(DownloadingFileStatus status) {
        return new DownloadingFile(id, name, ownerUsername, status, totalBytes, downloadedBytes, speedBytesPerSecond);
    }
}
