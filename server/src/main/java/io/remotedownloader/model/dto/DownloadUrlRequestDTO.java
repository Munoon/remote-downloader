package io.remotedownloader.model.dto;

public record DownloadUrlRequestDTO(
        String url,
        String fileName,
        String path
) {
}
