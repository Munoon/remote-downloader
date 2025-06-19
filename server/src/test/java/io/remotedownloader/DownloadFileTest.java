package io.remotedownloader;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.FilesHistoryReportDTO;
import io.remotedownloader.model.dto.ListFoldersResponseDTO;
import io.remotedownloader.model.dto.Page;
import io.remotedownloader.util.WebClient;
import io.remotedownloader.worker.DownloadingFilesReportWorker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static io.remotedownloader.util.TestUtil.assertWithReties;
import static io.remotedownloader.util.WebClient.loggedAdminWebClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DownloadFileTest extends BaseTest {
    @Test
    void downloadFile() throws IOException, InterruptedException {
        byte[] fileContent = "This is example file content.".getBytes(StandardCharsets.UTF_8);
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try (exchange) {
                    exchange.sendResponseHeaders(200, fileContent.length);
                    exchange.getResponseBody().write(fileContent);
                    exchange.getResponseBody().flush();
                }
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "example file.txt", null);
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("example file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(fileContent.length, file.totalBytes());

            verify(downloadFileHandler, times(1)).handle(any());
            verifyFileContent("example file.txt", "This is example file content.");
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void downloadFileServerReturnError() throws IOException, InterruptedException {
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try (exchange) {
                    exchange.sendResponseHeaders(404, 0);
                }
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null)
                    .verifyError(1, Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Server respond with an error.");

            verify(downloadFileHandler, times(1)).handle(any());
            assertFalse(Files.exists(Path.of(holder.serverProperties.getDownloadFolder(), "file.txt")));
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void chunkedFileDownload() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, 5);
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);

            ArgumentCaptor<HttpExchange> exchangeCaptor = ArgumentCaptor.forClass(HttpExchange.class);
            verify(downloadFileHandler, timeout(500).times(1)).handle(exchangeCaptor.capture());
            HttpExchange exchange = exchangeCaptor.getValue();

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "");

            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(new byte[]{'a', 'b'});
            responseBody.flush();

            assertWithReties(5, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADING, reportedFile.status());
                assertEquals(5, reportedFile.totalBytes());
                assertEquals(2, reportedFile.downloadedBytes());
            });
            verifyFileContent("file.txt", "ab");

            responseBody.write(new byte[]{'c', 'd', 'e'});
            responseBody.flush();
            exchange.close();

            assertWithReties(10, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADED, reportedFile.status());
                assertEquals(5, reportedFile.totalBytes());
                assertEquals(5, reportedFile.downloadedBytes());
            });
            verifyFileContent("file.txt", "abcde");
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void subDirectoriesCreated() throws IOException, InterruptedException {
        byte[] fileContent = "This is example file content.".getBytes(StandardCharsets.UTF_8);
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try (exchange) {
                    exchange.sendResponseHeaders(200, fileContent.length);
                    exchange.getResponseBody().write(fileContent);
                    exchange.getResponseBody().flush();
                }
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", "subFolderA/subFolderB");
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(fileContent.length, file.totalBytes());

            verify(downloadFileHandler, times(1)).handle(any());
            verifyFileContent("subFolderA/subFolderB/file.txt", "This is example file content.");
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void downloadFileAlreadyExists() throws IOException, InterruptedException {
        String downloadFolder = holder.serverProperties.getDownloadFolder();
        Files.createFile(Path.of(downloadFolder, "file.txt"));

        WebClient webClient = loggedAdminWebClient();
        webClient.downloadFile("https://127.0.0.1:18081", "file.txt", null)
                .verifyError(1, Error.ErrorTypes.FAILED_TO_DOWNLOAD, "The file with this name already exists.");
    }

    @Test
    void downloadFileValidation() throws InterruptedException {
        WebClient webClient = loggedAdminWebClient();

        webClient.downloadFile(null, null, null)
                .verifyError(1, Error.ErrorTypes.VALIDATION, "URL can't be null.");
        webClient.downloadFile("", null, null)
                .verifyError(2, Error.ErrorTypes.VALIDATION, "URL should not be empty.");
        webClient.downloadFile("http://localhost:18081/abc", null, null)
                .verifyError(3, Error.ErrorTypes.VALIDATION, "File name can't be null.");
        webClient.downloadFile("http://localhost:18081/abc", "", null)
                .verifyError(4, Error.ErrorTypes.VALIDATION, "File name should not be empty.");
        webClient.downloadFile("http://localhost:18081/abc", "a".repeat(1000), null)
                .verifyError(5, Error.ErrorTypes.VALIDATION, "File name is too long.");
        webClient.downloadFile("http://localhost:18081/abc", "a/b", null)
                .verifyError(6, Error.ErrorTypes.VALIDATION, "File name contain unallowed char.");
        webClient.downloadFile("http://localhost:18081/abc", "a\0b", null)
                .verifyError(7, Error.ErrorTypes.VALIDATION, "File name contain unallowed char.");
        webClient.downloadFile("http://localhost:18081/abc", "file.txt", "a\0b")
                .verifyError(8, Error.ErrorTypes.VALIDATION, "Path contain unallowed char.");
        webClient.downloadFile("http://localhost:18081/abc", "file.txt", "a".repeat(2000))
                .verifyError(9, Error.ErrorTypes.VALIDATION, "Path is too long.");
        webClient.downloadFile("http://localhost:18081/abc", "file.txt", "")
                .verifyError(10, Error.ErrorTypes.VALIDATION, "Path should not be empty.");
    }

    @Test
    void stopDownloadingFile() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, 5);
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);

            ArgumentCaptor<HttpExchange> exchangeCaptor = ArgumentCaptor.forClass(HttpExchange.class);
            verify(downloadFileHandler, timeout(500).times(1)).handle(exchangeCaptor.capture());
            HttpExchange exchange = exchangeCaptor.getValue();

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "");

            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(new byte[]{'a', 'b'});
            responseBody.flush();

            assertWithReties(5, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADING, reportedFile.status());
                assertEquals(5, reportedFile.totalBytes());
                assertEquals(2, reportedFile.downloadedBytes());
            });
            verifyFileContent("file.txt", "ab");

            file = webClient.stopDownloading(file.id()).parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.PAUSED, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(2, file.downloadedBytes());

            // checking, that the remote connection is closed
            assertWithReties(5, 200, () -> {
                assertThrows(IOException.class, () -> exchange.getResponseBody().write(1));
            });
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void stopNonDownloadingFile() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try (exchange) {
                    exchange.sendResponseHeaders(200, 3);
                    exchange.getResponseBody().write(new byte[]{'a', 'b', 'c'});
                    exchange.getResponseBody().flush();
                }
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);
            verify(downloadFileHandler, timeout(500).times(1)).handle(any());

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(3, file.totalBytes());

            assertWithReties(5, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADED, reportedFile.status());
                assertEquals(3, reportedFile.totalBytes());
                assertEquals(3, reportedFile.downloadedBytes());
            });
            verifyFileContent("file.txt", "abc");

            webClient.stopDownloading(file.id())
                    .verifyError(1, Error.ErrorTypes.FAILED_TO_DOWNLOAD, "File status should be 'Downloading'.");
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void stopDownloadingFileValidation() throws InterruptedException {
        WebClient webClient = loggedAdminWebClient();
        webClient.stopDownloading(null)
                .verifyError(1, Error.ErrorTypes.VALIDATION, "File ID can't be null.");
        webClient.stopDownloading("aaa")
                .verifyError(2, Error.ErrorTypes.NOT_FOUND, "File is not found.");
    }

    @Test
    void resumeDownloading() throws Throwable {
        AtomicInteger contentLength = new AtomicInteger(5);
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, contentLength.get());
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);

            ArgumentCaptor<HttpExchange> exchangeCaptor = ArgumentCaptor.forClass(HttpExchange.class);
            verify(downloadFileHandler, timeout(500).times(1)).handle(exchangeCaptor.capture());
            HttpExchange exchange = exchangeCaptor.getValue();

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "");

            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(new byte[]{'a', 'b'});
            responseBody.flush();

            assertWithReties(5, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADING, reportedFile.status());
                assertEquals(5, reportedFile.totalBytes());
                assertEquals(2, reportedFile.downloadedBytes());
            });
            verifyFileContent("file.txt", "ab");

            file = webClient.stopDownloading(file.id()).parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.PAUSED, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(2, file.downloadedBytes());

            Mockito.clearInvocations(downloadFileHandler);
            contentLength.set(3);
            file = webClient.resumeDownloading(file.id()).parseDownloadFile(2);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(2, file.downloadedBytes());

            exchangeCaptor = ArgumentCaptor.forClass(HttpExchange.class);
            verify(downloadFileHandler, timeout(500).times(1)).handle(exchangeCaptor.capture());
            exchange = exchangeCaptor.getValue();
            assertEquals("bytes=3-", exchange.getRequestHeaders().getFirst("Range"));
            
            responseBody = exchange.getResponseBody();
            responseBody.write(new byte[]{'c', 'd', 'e'});
            responseBody.flush();
            exchange.close();

            assertWithReties(5, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADED, reportedFile.status());
                assertEquals(5, reportedFile.totalBytes());
                assertEquals(5, reportedFile.downloadedBytes());
            });
            verifyFileContent("file.txt", "abcde");
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void resumeNonPausedFile() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);

        HttpHandler downloadFileHandler = exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 3);
                exchange.getResponseBody().write(new byte[]{'a', 'b', 'c'});
                exchange.getResponseBody().flush();
            }
        };
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(3, file.totalBytes());

            assertWithReties(5, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADED, reportedFile.status());
                assertEquals(3, reportedFile.totalBytes());
                assertEquals(3, reportedFile.downloadedBytes());
            });
            verifyFileContent("file.txt", "abc");

            webClient.resumeDownloading(file.id())
                    .verifyError(1, Error.ErrorTypes.FAILED_TO_DOWNLOAD, "File status should be 'Paused'.");
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void resumeDownloadingValidation() throws InterruptedException {
        WebClient webClient = loggedAdminWebClient();
        webClient.resumeDownloading(null)
                .verifyError(1, Error.ErrorTypes.VALIDATION, "File ID can't be null.");
        webClient.resumeDownloading("aaa")
                .verifyError(2, Error.ErrorTypes.NOT_FOUND, "File is not found.");
    }

    @Test
    void deleteFile() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, 5);
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);

            ArgumentCaptor<HttpExchange> exchangeCaptor = ArgumentCaptor.forClass(HttpExchange.class);
            verify(downloadFileHandler, timeout(500).times(1)).handle(exchangeCaptor.capture());
            HttpExchange exchange = exchangeCaptor.getValue();

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "");

            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(new byte[]{'a', 'b'});
            responseBody.flush();

            assertWithReties(5, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADING, reportedFile.status());
                assertEquals(5, reportedFile.totalBytes());
                assertEquals(2, reportedFile.downloadedBytes());
            });

            Path filePath = Path.of(holder.serverProperties.getDownloadFolder(), "file.txt");
            assertTrue(Files.exists(filePath));
            verifyFileContent("file.txt", "ab");

            webClient.deleteFile(file.id()).verifyOk(1);

            // checking, that the remote connection is closed
            assertWithReties(5, 200, () -> {
                assertThrows(IOException.class, () -> exchange.getResponseBody().write(1));
            });

            assertFalse(Files.exists(filePath));
        } finally {
            fileServer.stop(0);
        }
    }

    @Test
    void deleteFileValidation() throws InterruptedException {
        WebClient webClient = loggedAdminWebClient();
        webClient.deleteFile(null)
                .verifyError(1, Error.ErrorTypes.VALIDATION, "File ID can't be null.");
        webClient.deleteFile("aaa")
                .verifyError(2, Error.ErrorTypes.NOT_FOUND, "File is not found.");
    }

    @Test
    void listFolders() throws InterruptedException, IOException {
        String downloadFolder = holder.serverProperties.getDownloadFolder();
        WebClient webClient = loggedAdminWebClient();

        ListFoldersResponseDTO response = webClient.listFolders(null).parseListFoldersResponse(1);
        assertNotNull(response.files());
        assertEquals(0, response.files().size());

        Files.createDirectories(Path.of(downloadFolder, "test"));

        response = webClient.listFolders(null).parseListFoldersResponse(2);
        assertNotNull(response.files());
        assertEquals(1, response.files().size());
        assertEquals("test", response.files().getFirst().fileName());
        assertTrue(response.files().getFirst().folder());

        Files.createFile(Path.of(downloadFolder, "test.txt"));

        response = webClient.listFolders(null).parseListFoldersResponse(3);
        assertNotNull(response.files());
        assertEquals(2, response.files().size());
        assertEquals("test", response.files().getFirst().fileName());
        assertTrue(response.files().getFirst().folder());
        assertEquals("test.txt", response.files().getLast().fileName());
        assertFalse(response.files().getLast().folder());

        response = webClient.listFolders("test").parseListFoldersResponse(4);
        assertNotNull(response.files());
        assertEquals(0, response.files().size());

        Files.createDirectories(Path.of(downloadFolder, "test", "subFolder"));

        response = webClient.listFolders("test").parseListFoldersResponse(5);
        assertNotNull(response.files());
        assertEquals(1, response.files().size());
        assertEquals("subFolder", response.files().getFirst().fileName());
        assertTrue(response.files().getFirst().folder());

        Files.createDirectories(Path.of(downloadFolder, "test", "subFolder", "another"));

        response = webClient.listFolders("test/subFolder").parseListFoldersResponse(6);
        assertNotNull(response.files());
        assertEquals(1, response.files().size());
        assertEquals("another", response.files().getFirst().fileName());
        assertTrue(response.files().getFirst().folder());
    }

    @Test
    void listFoldersValidation() throws InterruptedException {
        WebClient webClient = loggedAdminWebClient();

        webClient.listFolders("")
                .verifyError(1, Error.ErrorTypes.VALIDATION, "Path should not be empty.");
        webClient.listFolders("a".repeat(2000))
                .verifyError(2, Error.ErrorTypes.VALIDATION, "Path is too long.");
        webClient.listFolders("a\0b")
                .verifyError(3, Error.ErrorTypes.VALIDATION, "Path contain unallowed char.");
    }

    @Test
    void getFilesHistory() throws InterruptedException, IOException {
        byte[] fileContent = "This is example file content.".getBytes(StandardCharsets.UTF_8);
        class DownloadFileHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try (exchange) {
                    exchange.sendResponseHeaders(200, fileContent.length);
                    exchange.getResponseBody().write(fileContent);
                    exchange.getResponseBody().flush();
                }
            }
        }

        HttpHandler downloadFileHandler = spy(new DownloadFileHandler());
        HttpServer fileServer = HttpServer.create(
                new InetSocketAddress(18081), 0, "/example-file.txt", downloadFileHandler);
        fileServer.start();

        WebClient webClient = loggedAdminWebClient();
        try {
            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file1.txt", null);
            DownloadFileDTO file1 = webClient.parseDownloadFile(1);
            assertEquals("file1.txt", file1.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file1.status());
            assertEquals(fileContent.length, file1.totalBytes());

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file2.txt", null);
            DownloadFileDTO file2 = webClient.parseDownloadFile(2);
            assertEquals("file2.txt", file2.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file2.status());
            assertEquals(fileContent.length, file2.totalBytes());

            Page<DownloadFileDTO> page = webClient.getFiles(0, 20).parseFilesPage(3);
            assertEquals(2, page.totalElements());
            assertNotNull(page.content());
            assertEquals(2, page.content().length);
            DownloadFileDTO file = page.content()[0];
            assertEquals(file1.id(), file.id());
            assertEquals("file1.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADED, file.status());
            assertEquals(fileContent.length, file.totalBytes());
            assertEquals(fileContent.length, file.downloadedBytes());
            file = page.content()[1];
            assertEquals(file2.id(), file.id());
            assertEquals("file2.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADED, file.status());
            assertEquals(fileContent.length, file.totalBytes());
            assertEquals(fileContent.length, file.downloadedBytes());

            page = webClient.getFiles(0, 1).parseFilesPage(4);
            assertEquals(2, page.totalElements());
            assertNotNull(page.content());
            assertEquals(1, page.content().length);
            file = page.content()[0];
            assertEquals(file1.id(), file.id());
            assertEquals("file1.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADED, file.status());
            assertEquals(fileContent.length, file.totalBytes());
            assertEquals(fileContent.length, file.downloadedBytes());

            page = webClient.getFiles(1, 1).parseFilesPage(5);
            assertEquals(2, page.totalElements());
            assertNotNull(page.content());
            assertEquals(1, page.content().length);
            file = page.content()[0];
            assertEquals(file2.id(), file.id());
            assertEquals("file2.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADED, file.status());
            assertEquals(fileContent.length, file.totalBytes());
            assertEquals(fileContent.length, file.downloadedBytes());

            page = webClient.getFiles(2, 1).parseFilesPage(6);
            assertEquals(2, page.totalElements());
            assertNotNull(page.content());
            assertEquals(0, page.content().length);
        } finally {
            fileServer.stop(0);
        }
    }

    private static void verifyFileContent(String fileName, String expectedContent) throws IOException {
        String downloadFolder = holder.serverProperties.getDownloadFolder();
        String content = Files.readString(Path.of(downloadFolder, fileName));
        assertEquals(expectedContent, content);
    }
}