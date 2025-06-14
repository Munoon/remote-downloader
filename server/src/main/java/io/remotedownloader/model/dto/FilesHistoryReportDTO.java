package io.remotedownloader.model.dto;

import java.util.List;

public record FilesHistoryReportDTO(
        List<DownloadFileDTO> files
) {
}
