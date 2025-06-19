package io.remotedownloader.downloader;

import io.netty.handler.codec.http.HttpHeaders;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

public abstract class BaseFileDownloader implements AsyncHandler<Object> {
    private static final Logger log = LogManager.getLogger(BaseFileDownloader.class);
    private final String url;
    private final SeekableByteChannel fileChannel;
    protected final Path filePath;
    protected final FilesStorageDao filesStorageDao;

    protected DownloadingFile file;
    private HttpResponseStatus responseStatus;
    private long previousChunkTime;
    private volatile boolean aborted;

    protected BaseFileDownloader(String url,
                                 Path filePath,
                                 SeekableByteChannel fileChannel,
                                 FilesStorageDao filesStorageDao) {
        this.url = url;
        this.filePath = filePath;
        this.fileChannel = fileChannel;
        this.filesStorageDao = filesStorageDao;
    }

    @Override
    public State onStatusReceived(HttpResponseStatus responseStatus) {
        this.responseStatus = responseStatus;
        int statusCode = responseStatus.getStatusCode();
        if (statusCode >= 200 && statusCode < 300) {
            log.info("Start downloading '{}' to '{}'", url, filePath);
            return State.CONTINUE;
        } else {
            log.info("Received {} response code from server when trying to download {}. Aborting...", statusCode, filePath);
            onStartFailure();
            markAborted();
            return State.ABORT;
        }
    }

    @Override
    public State onHeadersReceived(HttpHeaders headers) {
        onStartDownloading(this.responseStatus, headers);
        return State.CONTINUE;
    }

    @Override
    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
        try {
            ByteBuffer byteBuffer = bodyPart.getBodyByteBuffer();
            long size = byteBuffer.remaining();
            if (size == 0) {
                return State.CONTINUE;
            }

            fileChannel.write(byteBuffer);

            long now = System.nanoTime();

            if (log.isTraceEnabled()) {
                log.trace("Body part received for file {} [size = {}]", filePath, size);
            }

            if (file != null) {
                // as long, as we are writing to this field only from a single thread - this is fine
                //noinspection NonAtomicOperationOnVolatileField
                file.downloadedBytes += size;

                long durationMS = Math.max(now - previousChunkTime, 1_000_000) / 1_000_000;
                file.speedBytesPerMS = size / durationMS;
            }

            previousChunkTime = now;
        } catch (IOException e) {
            log.warn("Failed to write to a file {}", filePath);
            return State.ABORT;
        }
        return State.CONTINUE;
    }

    @Override
    public void onThrowable(Throwable t) {
        log.warn("Failed to download '{}'", filePath, t);
        closeFile();
        if (!aborted) {
            onError();
        }
    }

    @Override
    public Object onCompleted() {
        if (!aborted) {
            log.info("File '{}' has been downloaded", filePath);
            if (file != null) {
                markFile(DownloadingFileStatus.DOWNLOADED);
            }
        }
        closeFile();
        return null;
    }

    protected abstract void onStartFailure();
    protected abstract void onStartDownloading(HttpResponseStatus status, HttpHeaders headers);
    protected abstract void onError();

    private void closeFile() {
        try {
            fileChannel.close();
        } catch (IOException e) {
            log.warn("Failed to close a file", e);
        }
    }

    protected void markFile(DownloadingFileStatus status) {
        filesStorageDao.updateFile(file.withStatus(status));
    }

    public void markAborted() {
        this.aborted = true;
    }
}
