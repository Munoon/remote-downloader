package io.remotedownloader.model.dto;

import io.remotedownloader.util.ValidationUtil;

public record FileIdRequestDTO(
        String fileId
) implements Validatable {
    @Override
    public void validate() {
        ValidationUtil.nonNull(fileId, "File ID");
    }
}
