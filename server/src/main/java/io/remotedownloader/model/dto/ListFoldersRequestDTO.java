package io.remotedownloader.model.dto;

import io.remotedownloader.util.ValidationUtil;

public record ListFoldersRequestDTO(
        String path
) implements Validatable {
    @Override
    public void validate() {
        ValidationUtil.notEmpty(path, "Path");
        ValidationUtil.maxLength(path, 1_000, "Path");
        ValidationUtil.pathAllowedChars(path, "Path");
    }
}
