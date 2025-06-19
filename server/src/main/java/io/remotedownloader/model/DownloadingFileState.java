package io.remotedownloader.model;

import io.remotedownloader.downloader.BaseFileDownloader;
import org.asynchttpclient.ListenableFuture;

public record DownloadingFileState(
        ListenableFuture<?> future,
        BaseFileDownloader downloader
) {
    public void stopDownloading() {
        downloader.markAborted();
        future.done();
    }
}
