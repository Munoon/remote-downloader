package io.remotedownloader.protocol.dto;

public record Error(
        ErrorTypes type,
        String message
) {
    public enum ErrorTypes {
        UNKNOWN, UNKNOWN_COMMAND, FAILED_TO_DOWNLOAD
    }
}
