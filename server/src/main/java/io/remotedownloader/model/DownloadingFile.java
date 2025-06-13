package io.remotedownloader.model;

public final class DownloadingFile {
    public static final DownloadingFile[] EMPTY_ARRAY = new DownloadingFile[0];

    public final String id;
    public final String name;
    public final String path;
    public final String ownerUsername;
    public final DownloadingFileStatus status;
    public final long totalBytes;
    public volatile long downloadedBytes;
    public volatile long speedBytesPerSecond;

    public DownloadingFile(
            String id,
            String name,
            String path,
            String ownerUsername,
            DownloadingFileStatus status,
            long totalBytes,
            long downloadedBytes,
            long speedBytesPerSecond
    ) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.ownerUsername = ownerUsername;
        this.status = status;
        this.totalBytes = totalBytes;
        this.downloadedBytes = downloadedBytes;
        this.speedBytesPerSecond = speedBytesPerSecond;
    }

    public DownloadingFile withStatus(DownloadingFileStatus status) {
        return new DownloadingFile(
                id, name, path, ownerUsername, status, totalBytes, downloadedBytes, speedBytesPerSecond);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DownloadingFile that)) {
            return false;
        }

        return totalBytes == that.totalBytes
               && downloadedBytes == that.downloadedBytes
               && speedBytesPerSecond == that.speedBytesPerSecond
               && id.equals(that.id)
               && name.equals(that.name)
               && ownerUsername.equals(that.ownerUsername)
               && status == that.status;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + ownerUsername.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + Long.hashCode(totalBytes);
        result = 31 * result + Long.hashCode(downloadedBytes);
        result = 31 * result + Long.hashCode(speedBytesPerSecond);
        return result;
    }

    @Override
    public String toString() {
        return "DownloadingFile[" +
               "id=" + id + ", " +
               "name=" + name + ", " +
               "ownerUsername=" + ownerUsername + ", " +
               "status=" + status + ", " +
               "totalBytes=" + totalBytes + ", " +
               "downloadedBytes=" + downloadedBytes + ", " +
               "speedBytesPerSecond=" + speedBytesPerSecond + ']';
    }

}
