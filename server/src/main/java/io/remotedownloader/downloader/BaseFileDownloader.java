package io.remotedownloader.downloader;

import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.remotedownloader.ServerProperties;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

public abstract class BaseFileDownloader implements AsyncHandler<Object> {
    private static final Logger log = LogManager.getLogger(BaseFileDownloader.class);
    private final String url;
    protected final Path filePath;
    protected final FilesStorageDao filesStorageDao;
    private final long mapSize;
    private final int commitSize;

    protected DownloadingFile file;
    protected RandomAccessFile randomAccessFile;
    protected FileChannel fileChannel;
    private MappedByteBuffer buffer;
    private HttpResponseStatus responseStatus;
    private HttpHeaders headers;
    private volatile boolean aborted;

    private long secondStart;
    private long secondDownloadedBytes;

    protected BaseFileDownloader(String url,
                                 Path filePath,
                                 FilesStorageDao filesStorageDao,
                                 ServerProperties serverProperties) {
        this.url = url;
        this.filePath = filePath;
        this.filesStorageDao = filesStorageDao;
        this.mapSize = serverProperties.getFileMapSize();
        this.commitSize = serverProperties.getFileCommitSize();
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
            this.aborted = true;
            return State.ABORT;
        }
    }

    @Override
    public State onHeadersReceived(HttpHeaders headers) {
        if (this.headers == null) {
            this.headers = headers;
        } else {
            this.headers.add(headers);
        }
        return State.CONTINUE;
    }

    @Override
    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
        try {
            if (fileChannel == null) {
                HttpHeaders headers = this.headers == null ? EmptyHttpHeaders.INSTANCE : this.headers;
                boolean success = onStartDownloading(this.responseStatus, headers);
                if (!success) {
                    this.aborted = true;
                    return State.ABORT;
                }

                this.buffer = allocateBuffer(file.downloadedBytes);
                return onBodyPartReceived(bodyPart); // ResumeFileDownloader may actually skip some bytes
            }

            ByteBuffer chunk = bodyPart.getBodyByteBuffer();
            int size = chunk.remaining();

            long previouslyDownloadedBytes = file.downloadedBytes;
            long fileOffset = previouslyDownloadedBytes;
            while (chunk.hasRemaining()) {
                int remaining = this.buffer.remaining();
                if (remaining < chunk.remaining()) {
                    if (remaining > 0) {
                        int originalChunkLimit = chunk.limit();
                        chunk.limit(chunk.position() + remaining);
                        this.buffer.put(chunk);
                        chunk.limit(originalChunkLimit);
                        fileOffset += remaining;
                    }

                    this.buffer = allocateBuffer(fileOffset);
                } else {
                    this.buffer.put(chunk);
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("Body part received for file {} [size = {}]", filePath, size);
            }

            long downloadedBytes = previouslyDownloadedBytes + size;
            if ((previouslyDownloadedBytes / commitSize) != (downloadedBytes / commitSize)) {
                if (log.isTraceEnabled()) {
                    log.trace("Commiting file {}, downloaded bytes = {}", filePath, downloadedBytes);
                }
                this.file = file.commitBytes(DownloadingFileStatus.DOWNLOADING, downloadedBytes);
                filesStorageDao.updateFile(file);
                fileChannel.force(false);
            } else {
                // as long, as we are writing to this field only from a single thread - this is fine
                file.downloadedBytes = downloadedBytes;
            }

            long now = System.nanoTime();
            if (secondStart == 0) {
                this.secondStart = now;
            } else if (now - secondStart >= 1000000000) {
                file.speedBytesPerSecond = secondDownloadedBytes;
                secondDownloadedBytes = 0;
                secondStart = now;
            } else {
                secondDownloadedBytes += size;
            }
        } catch (IOException e) {
            log.warn("Failed to write to a file {}", filePath);
            return State.ABORT;
        }
        return State.CONTINUE;
    }

    private MappedByteBuffer allocateBuffer(long fileOffset) throws IOException {
        long remainingBytes = file.totalBytes - fileOffset;
        long size = remainingBytes > 0 ? Math.min(mapSize, remainingBytes) : mapSize;
        return fileChannel.map(FileChannel.MapMode.READ_WRITE, fileOffset, size);
    }

    @Override
    public void onThrowable(Throwable t) {
        try {
            if (!(t instanceof CancellationException)) {
                log.warn("Failed to download '{}'", filePath, t);
                if (!aborted) {
                    onError();
                }
            }
        } finally {
            closeFile();
        }
    }

    @Override
    public Object onCompleted() {
        try {
            if (!aborted) {
                log.info("File '{}' has been downloaded", filePath);
                if (file != null) {
                    markFile(DownloadingFileStatus.DOWNLOADED);
                    try {
                        randomAccessFile.setLength(file.commitedDownloadedBytes);
                    } catch (Exception e) {
                        log.warn("Failed to update file '{}' length", filePath);
                    }
                }
            }
            return null;
        } finally {
            closeFile();
        }
    }

    protected abstract void onStartFailure();
    protected abstract boolean onStartDownloading(HttpResponseStatus status, HttpHeaders headers);
    protected abstract void onError();

    private void closeFile() {
        if (fileChannel != null) {
            try {
                try {
                    fileChannel.force(false);
                } finally {
                    fileChannel.close();
                }
            } catch (Exception e) {
                log.warn("Failed to close a file", e);
            }
        }
    }

    protected void markFile(DownloadingFileStatus status) {
        this.file = file.commitBytes(status, file.downloadedBytes);
        filesStorageDao.updateFile(file);
    }
}
