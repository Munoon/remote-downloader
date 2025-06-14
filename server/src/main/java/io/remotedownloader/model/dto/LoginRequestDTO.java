package io.remotedownloader.model.dto;

public record LoginRequestDTO(
        String username,
        String password,
        boolean subscribeOnDownloadingFilesReport
) {
}
