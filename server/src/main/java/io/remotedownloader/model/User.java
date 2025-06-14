package io.remotedownloader.model;

public record User(
        String username,
        String encryptedPassword,
        boolean isAdmin,
        long createdAt
) implements StorageRecord<String> {
    @Override
    public String getId() {
        return username;
    }

    @Override
    public StorageModel getModel() {
        return StorageModel.USER;
    }
}
