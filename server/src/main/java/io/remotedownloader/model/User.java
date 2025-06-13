package io.remotedownloader.model;

public record User(
        String username,
        String encryptedPassword,
        boolean isAdmin,
        long createdAt
) {
}
