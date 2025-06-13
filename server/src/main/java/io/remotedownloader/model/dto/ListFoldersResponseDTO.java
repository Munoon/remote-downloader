package io.remotedownloader.model.dto;

import java.util.List;

public record ListFoldersResponseDTO(
        boolean canDownload,
        List<ListFileDTO> files
) {
}
