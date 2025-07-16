package io.remotedownloader.dao;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.epoll.Epoll;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.OpenSsl;
import io.remotedownloader.ServerProperties;
import io.remotedownloader.downloader.BaseFileDownloader;
import io.remotedownloader.downloader.NewFileDownloader;
import io.remotedownloader.downloader.ResumeFileDownloader;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.ListFileDTO;
import io.remotedownloader.protocol.ErrorException;
import io.remotedownloader.protocol.StringMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ListenableFuture;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
                              TransportTypeHolder transportTypeHolder,
                              FilesStorageDao filesStorageDao,
                              ThreadPoolsHolder threadPoolsHolder) {
        this.properties = properties;
        this.filesStorageDao = filesStorageDao;
        this.threadPoolsHolder = threadPoolsHolder;

        DefaultAsyncHttpClientConfig httpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
                .setRequestTimeout(Duration.ofSeconds(-1))
                .setMaxRedirects(properties.getMaxRedirects())
                .setKeepAlive(false)
                .setUserAgent("Remote-Downloader/1.0")
                .setMaxRequestRetry(properties.getMaxRequestRetry())
                .setUseOpenSsl(OpenSsl.isAvailable())
                .setUseNativeTransport(Epoll.isAvailable())
                .setResponseBodyPartFactory(AsyncHttpClientConfig.ResponseBodyPartFactory.LAZY)
//                .setEventLoopGroup(transportTypeHolder.workerGroup)
                .build();
        this.asyncHttpClient = new DefaultAsyncHttpClient(httpClientConfig);

        for (DownloadingFile file : filesStorageDao.getAllFiles()) {
            if (file.status == DownloadingFileStatus.DOWNLOADING) {
                resumeDownloading(null, null, file)
                        .exceptionally(e -> {
                            log.warn("Failed to resume downloading file '{}' after boot", file.name, e);
                            filesStorageDao.updateFile(file.commitBytes(
                                    DownloadingFileStatus.ERROR,
                                    file.downloadedBytes));
                            return null;
                        });
            }
        }
    }

    public CompletableFuture<Void> download(ChannelHandlerContext ctx,
                                            StringMessage msg,
                                            DownloadUrlRequestDTO req,
                                            String username) {
        return CompletableFuture.runAsync(() -> {
            Path filePath = resolveFilePath(req.path(), req.fileName(), true);
            try {
                Files.createFile(filePath);
            } catch (FileAlreadyExistsException e) {
                throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "The file with this name already exists.", e);
            } catch (Exception e) {
                throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a file on a server.", e);
            }

            String fileId = UUID.randomUUID().toString();

            NewFileDownloader handler = new NewFileDownloader(
                    ctx, msg, fileId, username, req, filePath, filesStorageDao, properties);
            startDownloading(req.url(), fileId, handler, 0);
        }, threadPoolsHolder.blockingTasksExecutor);
    }

    public CompletableFuture<Void> resumeDownloading(ChannelHandlerContext ctx,
                                                     StringMessage msg,
                                                     DownloadingFile file) {
        return CompletableFuture.runAsync(() -> {
            Path filePath = resolveFilePath(file.path, file.name, false);

            long downloadedBytes = file.commitedDownloadedBytes;
            file.downloadedBytes = downloadedBytes;

            ResumeFileDownloader handler = new ResumeFileDownloader(
                    ctx, msg, file, filePath, filesStorageDao, properties);
            startDownloading(file.url, file.id, handler, downloadedBytes);
        }, threadPoolsHolder.blockingTasksExecutor);
    }

    private void startDownloading(String url, String fileId, BaseFileDownloader handler, long rangeOffset) {
        BoundRequestBuilder requestBuilder = asyncHttpClient.prepareGet(url)
                .setFollowRedirect(properties.getFollowRedirect())
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));

        if (rangeOffset != 0) {
            requestBuilder.addHeader(HttpHeaderNames.RANGE, "bytes=" + rangeOffset + '-');
        }

        ListenableFuture<Object> future = requestBuilder.execute(handler);
        downloadingFiles.put(fileId, future);

        future.addListener(() -> downloadingFiles.remove(fileId), null);
    }

    public void stopDownloading(String fileId) {
        ListenableFuture<?> future = downloadingFiles.remove(fileId);
        if (future != null) {
            future.cancel(true);
        }
    }

    public void deleteFile(DownloadingFile file) {
        threadPoolsHolder.blockingTasksExecutor.execute(() -> {
            Path path = resolveFilePath(file.path, file.name, false);
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("Failed to delete the file from the server", e);
            }
        });
    }

    public CompletableFuture<List<ListFileDTO>> listFolders(String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path baseDownloadFolder = resolveDownloadFolder();

                Path downloadFolder = baseDownloadFolder;
                if (path != null && !path.isBlank()) {
                    downloadFolder = downloadFolder.resolve(path).normalize();
                    if (!downloadFolder.startsWith(baseDownloadFolder)) {
                        throw new ErrorException(Error.ErrorTypes.VALIDATION, "Access to this folder is denied!");
                    }
                }

                try (Stream<Path> files = Files.list(downloadFolder)) {
                    return files
                            .map(file -> new ListFileDTO(Files.isDirectory(file), file.getFileName().toString()))
                            // folders should always goes first
                            .sorted((f1, f2) -> Boolean.compare(f2.folder(), f1.folder()))
                            .toList();
                }
            } catch (ErrorException e) {
                throw e;
            } catch (Exception e) {
                throw new ErrorException(Error.ErrorTypes.UNKNOWN, "Failed to list folders on server.", e);
            }
        }, threadPoolsHolder.blockingTasksExecutor);
    }

    private Path resolveFilePath(String path, String fileName, boolean createFolders) {
        Path baseDownloadFolder = resolveDownloadFolder();

        Path downloadFolder = baseDownloadFolder;
        if (path != null && !path.isBlank()) {
            downloadFolder = downloadFolder.resolve(path).normalize();
            if (!downloadFolder.startsWith(baseDownloadFolder)) {
                throw new ErrorException(Error.ErrorTypes.VALIDATION, "Access to this folder is denied!");
            }

            if (createFolders) {
                if (Files.exists(downloadFolder)) {
                    if (!Files.isDirectory(downloadFolder)) {
                        throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Specified directory is a file.");
                    }
                } else {
                    try {
                        Files.createDirectories(downloadFolder);
                    } catch (Exception e) {
                        throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a directory", e);
                    }
                }
            }
        }
        return downloadFolder.resolve(fileName);
    }

    private Path resolveDownloadFolder() {
        Path downloadFolder = Path.of(properties.getDownloadFolder());
        if (!Files.exists(downloadFolder)) {
            try {
                Files.createDirectory(downloadFolder);
            } catch (Exception e) {
                throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a download folder.", e);
            }
        } else if (!Files.isDirectory(downloadFolder)) {
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Download folder is a file.");
        }
        return downloadFolder;
    }

    public void clear() {
        downloadingFiles.clear();
    }
}
