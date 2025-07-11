package io.remotedownloader.protocol;

import io.remotedownloader.model.dto.Error;

public class ErrorException extends RuntimeException {
    public final Error error;

    public ErrorException(Error.ErrorTypes errorType, String message) {
        super(message);
        this.error = new Error(errorType, message);
    }

    public ErrorException(Error.ErrorTypes errorType, String message, Throwable cause) {
        super(message, cause);
        this.error = new Error(errorType, message);
    }
}
