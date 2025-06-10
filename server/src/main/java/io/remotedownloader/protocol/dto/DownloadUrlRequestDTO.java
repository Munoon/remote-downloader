package io.remotedownloader.protocol.dto;

public record DownloadUrlRequestDTO(
        String url,
        String fileName,
        String path
) {
}
