package io.remotedownloader.model.dto;

import io.remotedownloader.util.ValidationUtil;

public record DownloadUrlRequestDTO(
        String url,
        String fileName,
        String path
) implements Validatable {
    @Override
    public void validate() {
        // TODO improve
        ValidationUtil.nonNull(url, "URL");
        ValidationUtil.notEmpty(url, "URL");

        // TODO improve
        ValidationUtil.nonNull(fileName, "File name");
        ValidationUtil.notEmpty(fileName, "File name");
        ValidationUtil.maxLength(fileName, 255, "File name");
        ValidationUtil.fileNameAllowedChars(fileName, "File name");

        // TODO improve
        ValidationUtil.notEmpty(path, "Path");
        ValidationUtil.maxLength(path, 1_000, "Path");
    }
}
