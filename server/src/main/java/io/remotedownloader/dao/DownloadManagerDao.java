package io.remotedownloader.dao;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.FileDownloader;
import io.remotedownloader.ServerProperties;
import io.remotedownloader.model.DownloadingFile;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

public class DownloadManagerDao {
    private static final Logger log = LogManager.getLogger(DownloadManagerDao.class);
    private final ConcurrentMap<String, ListenableFuture<?>> downloadingFiles = new ConcurrentHashMap<>();
    private final ServerProperties properties;
    private final AsyncHttpClient asyncHttpClient;
    private final FilesStorageDao filesStorageDao;

    public DownloadManagerDao(ServerProperties properties,
                              AsyncHttpClient asyncHttpClient,
                              FilesStorageDao filesStorageDao) {
        this.properties = properties;
        this.asyncHttpClient = asyncHttpClient;
        this.filesStorageDao = filesStorageDao;
    }

    public void download(ChannelHandlerContext ctx, StringMessage msg, DownloadUrlRequestDTO req, String username) {
        Path filePath = resolveFilePath(req.path(), req.fileName());
        SeekableByteChannel fileChannel = createFileChannel(filePath);

        String fileId = UUID.randomUUID().toString();
        ListenableFuture<Object> future = asyncHttpClient.prepareGet(req.url())
                .execute(new FileDownloader(ctx, msg, fileId, username, req, filePath, fileChannel, filesStorageDao));
        downloadingFiles.put(fileId, future);

        future.addListener(() -> downloadingFiles.remove(fileId), null);
    }

    public void stopDownloading(String fileId) {
        ListenableFuture<?> future = downloadingFiles.get(fileId);
        if (future != null) {
            future.done();
        }
    }

    public void deleteFile(DownloadingFile file) {
        Path path = resolveFilePath(file.path, file.name);
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Failed to delete the file from the server", e);
        }
    }

    public ListFoldersResponseDTO listFolders(String path) {
        try {
            Path downloadFolder = resolveDownloadFolder();
            if (path != null) {
                downloadFolder = downloadFolder.resolve(path);
            }

            try (Stream<Path> files = Files.list(downloadFolder)) {
                List<ListFileDTO> result = files
                        .map(file -> new ListFileDTO(Files.isDirectory(file), file.getFileName().toString()))
                        .toList();
                return new ListFoldersResponseDTO(true, result);
            }
        } catch (Exception e) {
            log.warn("Failed to list files in download folder", e);
            return new ListFoldersResponseDTO(false, Collections.emptyList());
        }
    }

    private static SeekableByteChannel createFileChannel(Path filePath) {
        try {
            return Files.newByteChannel(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception e) {
            log.warn("Failed to create a file '{}'", filePath, e);
            throw new ErrorException(Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Failed to create a file on a server.");
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
