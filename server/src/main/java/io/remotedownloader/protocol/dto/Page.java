package io.remotedownloader.protocol.dto;

public record Page<T>(
        T[] content,
        int totalElements
) {
}
