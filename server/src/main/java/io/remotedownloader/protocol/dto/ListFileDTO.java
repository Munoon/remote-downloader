package io.remotedownloader.protocol.dto;

public record ListFileDTO(
        boolean folder,
        String fileName
) {
}
