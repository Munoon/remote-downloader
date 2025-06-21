package io.remotedownloader.model.dto;

import io.remotedownloader.util.ValidationUtil;

public record GetFilesHistoryRequestDTO(
        int offset,
        int size
) implements Validatable {
    @Override
    public void validate() {
        ValidationUtil.min(size, 1, "Size");
        ValidationUtil.max(size, 100, "Size");
    }
}
