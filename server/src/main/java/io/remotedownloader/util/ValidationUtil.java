package io.remotedownloader.util;

import io.remotedownloader.model.dto.Error;
import io.remotedownloader.protocol.ErrorException;

public class ValidationUtil {
    private static String BASIC_ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.@";

    public static void nonNull(Object value, String fieldName) {
        if (value == null) {
            throw new ErrorException(Error.ErrorTypes.VALIDATION, fieldName + " can't be null.");
        }
    }

    public static void maxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new ErrorException(Error.ErrorTypes.VALIDATION, fieldName + " is too long.");
        }
    }

    public static void notEmpty(String value, String fieldName) {
        if (value != null && value.isEmpty()) {
            throw new ErrorException(Error.ErrorTypes.VALIDATION, fieldName + " should not be empty.");
        }
    }

    public static void basicAllowedChars(String value, String fieldName) {
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (BASIC_ALLOWED_CHARS.indexOf(c) == -1) {
                    throw new ErrorException(Error.ErrorTypes.VALIDATION, fieldName + " contain unallowed char.");
                }
            }
        }
    }

    public static void fileNameAllowedChars(String value, String fieldName) {
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '/' || c == '\0') {
                    throw new ErrorException(Error.ErrorTypes.VALIDATION, fieldName + " contain unallowed char.");
                }
            }
        }
    }
}
