package io.remotedownloader.model.dto;

public record Page<T>(
        T[] content,
        int totalElements
) {
}
