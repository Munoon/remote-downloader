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
    public final String url;
    public final String ownerUsername;
    public final DownloadingFileStatus status;
    public final long totalBytes;
    public final long commitedDownloadedBytes;
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
            @JsonProperty("url") String url,
            @JsonProperty("ownerUsername") String ownerUsername,
            @JsonProperty("status") DownloadingFileStatus status,
            @JsonProperty("totalBytes") long totalBytes,
            @JsonProperty("commitedDownloadedBytes") long commitedDownloadedBytes,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("updatedAt") long updatedAt
    ) {
        this(
                id,
                name,
                path,
                url,
                ownerUsername,
                status,
                totalBytes,
                commitedDownloadedBytes,
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
            String url,
            String ownerUsername,
            DownloadingFileStatus status,
            long totalBytes,
            long commitedDownloadedBytes,
            long createdAt,
            long updatedAt,
            long downloadedBytes,
            long speedBytesPerMS
    ) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.ownerUsername = ownerUsername;
        this.url = url;
        this.status = status;
        this.totalBytes = totalBytes;
        this.commitedDownloadedBytes = commitedDownloadedBytes;
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
                url,
                ownerUsername,
                status,
                totalBytes,
                commitedDownloadedBytes,
                createdAt,
                System.currentTimeMillis(),
                downloadedBytes,
                speedBytesPerMS
        );
    }

    public DownloadingFile commitBytes(DownloadingFileStatus status, long downloadedBytes) {
        return new DownloadingFile(
                id,
                name,
                path,
                url,
                ownerUsername,
                status,
                totalBytes,
                downloadedBytes,
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
               && url.equals(that.url)
               && ownerUsername.equals(that.ownerUsername)
               && status == that.status;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + Objects.hashCode(path);
        result = 31 * result + url.hashCode();
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
               ", url='" + url + '\'' +
               ", ownerUsername='" + ownerUsername + '\'' +
               ", status=" + status +
               ", totalBytes=" + totalBytes +
               '}';
    }
}
