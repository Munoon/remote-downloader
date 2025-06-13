package io.remotedownloader.model.dto;

import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;

public record DownloadFileDTO(
        String id,
        String name,
        DownloadingFileStatus status,
        long totalBytes,
        long downloadedBytes,
        long speedBytesPerSecond
) {
    public DownloadFileDTO(DownloadingFile file) {
        this(
                file.id,
                file.name,
                file.status,
                file.totalBytes,
                file.downloadedBytes,
                file.speedBytesPerSecond
        );
    }
}
