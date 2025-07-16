package io.remotedownloader.model.dto;

import io.remotedownloader.protocol.ErrorException;
import io.remotedownloader.util.ValidationUtil;

import java.nio.file.Path;

public record DownloadUrlRequestDTO(
        String url,
        String fileName,
        String path
) implements Validatable {
    @Override
    public void validate() {
        // TODO improve
        ValidationUtil.nonNull(url, "URL");
        ValidationUtil.notBlank(url, "URL");

        ValidationUtil.nonNull(fileName, "File name");
        ValidationUtil.notBlank(fileName, "File name");
        ValidationUtil.maxLength(fileName, 255, "File name");
        ValidationUtil.fileNameAllowedChars(fileName, "File name");
        if (Path.of(fileName).normalize().getNameCount() != 1) {
            throw new ErrorException(Error.ErrorTypes.VALIDATION, "File name contain unallowed char.");
        }

        ValidationUtil.notBlank(path, "Path");
        ValidationUtil.maxLength(path, 1_000, "Path");
        ValidationUtil.pathAllowedChars(path, "Path");
    }
}
