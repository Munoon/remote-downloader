package io.remotedownloader.model.dto;

import io.remotedownloader.util.ValidationUtil;

public record LoginRequestDTO(
        String username,
        String password,
        boolean subscribeOnDownloadingFilesReport
) implements Validatable {
    @Override
    public void validate() {
        ValidationUtil.nonNull(username, "Username");
        ValidationUtil.notEmpty(username, "Username");
        ValidationUtil.maxLength(username, 255, "Username");
        ValidationUtil.basicAllowedChars(username, "Username");

        ValidationUtil.nonNull(password, "Password");
        ValidationUtil.notEmpty(username, "Password");
        ValidationUtil.maxLength(username, 1_000, "Password");
    }
}
