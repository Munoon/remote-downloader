package io.remotedownloader.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class DownloadingFile implements StorageRecord<String> {
    public static final DownloadingFile[] EMPTY_ARRAY = new DownloadingFile[0];

    @JsonProperty
    public final String id;
    public final String name;
    public final String path;
    public final String ownerUsername;
    public final DownloadingFileStatus status;
    public final long totalBytes;
    public final long createdAt;
    public final long updatedAt;

    // should be used just on the UI
    @JsonIgnore
    public volatile long downloadedBytes;
    @JsonIgnore
    public volatile long speedBytesPerMS;

    @JsonCreator
    public DownloadingFile(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("path") String path,
            @JsonProperty("ownerUsername") String ownerUsername,
            @JsonProperty("status") DownloadingFileStatus status,
            @JsonProperty("totalBytes") long totalBytes,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("updatedAt") long updatedAt
    ) {
        this(
                id,
                name,
                path,
                ownerUsername,
                status,
                totalBytes,
                createdAt,
                updatedAt,
                0,
                0
        );
    }

    public DownloadingFile(
            String id,
            String name,
            String path,
            String ownerUsername,
            DownloadingFileStatus status,
            long totalBytes,
            long createdAt,
            long updatedAt,
            long downloadedBytes,
            long speedBytesPerMS
    ) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.ownerUsername = ownerUsername;
        this.status = status;
        this.totalBytes = totalBytes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.downloadedBytes = downloadedBytes;
        this.speedBytesPerMS = speedBytesPerMS;
    }

    public DownloadingFile withStatus(DownloadingFileStatus status) {
        return new DownloadingFile(
                id,
                name,
                path,
                ownerUsername,
                status,
                totalBytes,
                createdAt,
                System.currentTimeMillis(),
                downloadedBytes,
                speedBytesPerMS
        );
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public StorageModel getModel() {
        return StorageModel.DOWNLOADING_FILE;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DownloadingFile that)) {
            return false;
        }

        return totalBytes == that.totalBytes
               && id.equals(that.id)
               && name.equals(that.name)
               && Objects.equals(path, that.path)
               && ownerUsername.equals(that.ownerUsername)
               && status == that.status;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + Objects.hashCode(path);
        result = 31 * result + ownerUsername.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + Long.hashCode(totalBytes);
        return result;
    }

    @Override
    public String toString() {
        return "DownloadingFile{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", path='" + path + '\'' +
               ", ownerUsername='" + ownerUsername + '\'' +
               ", status=" + status +
               ", totalBytes=" + totalBytes +
               '}';
    }
}
