package io.remotedownloader.model.dto;

import java.util.List;

public record ListFoldersResponseDTO(
        List<ListFileDTO> files
) {
}
