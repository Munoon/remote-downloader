package io.remotedownloader.model.dto;

import io.remotedownloader.model.DownloadingFile;

import java.util.List;

public record FilesHistoryReportDTO(
        List<DownloadingFile> files
) {
}
