package io.remotedownloader.dao;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.ServerProperties;
import io.remotedownloader.downloader.BaseFileDownloader;
import io.remotedownloader.downloader.NewFileDownloader;
import io.remotedownloader.downloader.ResumeFileDownloader;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.ListFileDTO;
import io.remotedownloader.model.dto.ListFoldersResponseDTO;
import io.remotedownloader.protocol.ErrorException;
import io.remotedownloader.protocol.StringMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ListenableFuture;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

public class DownloadManagerDao {
    private static final Logger log = LogManager.getLogger(DownloadManagerDao.class);
    private final ConcurrentMap<String, ListenableFuture<?>> downloadingFiles = new ConcurrentHashMap<>();
    private final ServerProperties properties;
    private final AsyncHttpClient asyncHttpClient;
    private final FilesStorageDao filesStorageDao;
    private final ThreadPoolsHolder threadPoolsHolder;

    public DownloadManagerDao(ServerProperties properties,
                              AsyncHttpClient asyncHttpClient,
                              FilesStorageDao filesStorageDao,
                              ThreadPoolsHolder threadPoolsHolder) {
        this.properties = properties;
        this.asyncHttpClient = asyncHttpClient;
        this.filesStorageDao = filesStorageDao;
        this.threadPoolsHolder = threadPoolsHolder;

        for (DownloadingFile file : filesStorageDao.getAllFiles()) {
            if (file.status == DownloadingFileStatus.DOWNLOADING) {
                resumeDownloading(null, null, file);
            }
        }
    }

    public CompletableFuture<Void> download(ChannelHandlerContext ctx,
                                            StringMessage msg,
                                            DownloadUrlRequestDTO req,
                                            String username) {
        return CompletableFuture.runAsync(() -> {
            Path filePath = resolveFilePath(req.path(), req.fileName());
            SeekableByteChannel fileChannel = createFileChannel(filePath);

            String fileId = UUID.randomUUID().toString();

            NewFileDownloader handler = new NewFileDownloader(
                    ctx, msg, fileId, username, req, filePath, fileChannel, filesStorageDao);
            startDownloading(req.url(), fileId, handler, 0);
        }, threadPoolsHolder.blockingTasksExecutor);
    }

    public CompletableFuture<Void> resumeDownloading(ChannelHandlerContext ctx,
                                                     StringMessage msg,
                                                     DownloadingFile file) {
        return CompletableFuture.runAsync(() -> {
            Path filePath = resolveFilePath(file.path, file.name);
            SeekableByteChannel fileChannel = openFileChannel(filePath);

            long downloadedBytes = filePath.toFile().length();
            file.downloadedBytes = downloadedBytes;

            ResumeFileDownloader handler = new ResumeFileDownloader(
                    ctx, msg, file, filePath, fileChannel, filesStorageDao);
            startDownloading(file.url, file.id, handler, downloadedBytes + 1);
        }, threadPoolsHolder.blockingTasksExecutor);
    }

    private void startDownloading(String url, String fileId, BaseFileDownloader handler, long rangeOffset) {
        ListenableFuture<Object> future = asyncHttpClient.prepareGet(url)
                .setRangeOffset(rangeOffset)
                .execute(handler);
        downloadingFiles.put(fileId, future);

        future.addListener(() -> downloadingFiles.remove(fileId), null);
    }

    public void stopDownloading(String fileId) {
        ListenableFuture<?> future = downloadingFiles.remove(fileId);
        if (future != null) {
            future.done();
        }
    }

    public void deleteFile(DownloadingFile file) {
        threadPoolsHolder.blockingTasksExecutor.execute(() -> {
            Path path = resolveFilePath(file.path, file.name);
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("Failed to delete the file from the server", e);
            }
        });
    }

    public CompletableFuture<ListFoldersResponseDTO> listFolders(String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path downloadFolder = resolveDownloadFolder();
                if (path != null) {
                    downloadFolder = downloadFolder.resolve(path);
                }

                try (Stream<Path> files = Files.list(downloadFolder)) {
                    List<ListFileDTO> result = files
                            .map(file -> new ListFileDTO(Files.isDirectory(file), file.getFileName().toString()))
                            .toList();
                    return new ListFoldersResponseDTO(result);
                }
            } catch (Exception e) {
                log.warn("Failed to list files in download folder", e);
                throw new ErrorException(Error.ErrorTypes.UNKNOWN, "Failed to list folders on server.");
            }
        }, threadPoolsHolder.blockingTasksExecutor);
    }

    private static SeekableByteChannel createFileChannel(Path filePath) {
        try {
            return Files.newByteChannel(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception e) {
            log.warn("Failed to create a file '{}'", filePath, e);
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a file on a server.");
        }
    }

    private static SeekableByteChannel openFileChannel(Path filePath) {
        try {
            return Files.newByteChannel(filePath, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (Exception e) {
            log.warn("Failed to open a file '{}'", filePath, e);
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to open a file on a server.");
        }
    }

    private Path resolveFilePath(String path, String fileName) {
        Path downloadFolder = resolveDownloadFolder();
        if (path != null) {
            downloadFolder = downloadFolder.resolve(path);
        }
        return downloadFolder.resolve(fileName);
    }

    private Path resolveDownloadFolder() {
        Path downloadFolder = Path.of(properties.getDownloadFolder());
        if (!Files.exists(downloadFolder)) {
            try {
                Files.createDirectory(downloadFolder);
            } catch (Exception e) {
                log.warn("Failed to create a download folder '{}'", downloadFolder, e);
                throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a download folder.");
            }
        } else if (!Files.isDirectory(downloadFolder)) {
            log.warn("The download folder is actually file.");
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Download folder is a file.");
        }
        return downloadFolder;
    }
}
