package io.remotedownloader.protocol.dto;

public record FileDTO(
        String id,
        String name,
        FileStatus status,
        long totalBytes,
        long downloadedBytes,
        long speedBytesPerSecond
) {
}
