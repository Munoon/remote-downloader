package io.remotedownloader.model.dto;

public record Error(
        ErrorTypes type,
        String message
) {
    public enum ErrorTypes {
        UNKNOWN,
        UNKNOWN_COMMAND,
        FAILED_TO_DOWNLOAD,
        NOT_FOUND,
        INCORRECT_CREDENTIALS,
        NOT_AUTHENTICATED,
        ALREADY_AUTHENTICATED
    }
}
