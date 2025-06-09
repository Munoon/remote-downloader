package io.remotedownloader.protocol.dto;

import java.util.List;

public record ListFoldersResponseDTO(
        boolean canDownload,
        List<ListFileDTO> files
) {
}
