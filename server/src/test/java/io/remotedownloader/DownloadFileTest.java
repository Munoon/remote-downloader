package io.remotedownloader;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.FilesHistoryReportDTO;
import io.remotedownloader.model.dto.ListFoldersResponseDTO;
import io.remotedownloader.model.dto.Page;
import io.remotedownloader.util.TestFileServer;
import io.remotedownloader.util.WebClient;
import io.remotedownloader.worker.DownloadingFilesReportWorker;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.remotedownloader.util.TestUtil.assertWithReties;
import static io.remotedownloader.util.WebClient.loggedAdminWebClient;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DownloadFileTest extends BaseTest {
    @Test
    void downloadFile() throws Exception {
        byte[] fileContent = "This is example file content.".getBytes(StandardCharsets.UTF_8);
        TestFileServer fileServer = TestFileServer.simpleFileServer(fileContent);

        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "example file.txt", null);
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("example file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(fileContent.length, file.totalBytes());

            fileServer.verifyRequest();
            verifyFileContent("example file.txt", "This is example file content.");
        } finally {
            fileServer.close();
        }
    }

    @Test
    void downloadFileServerReturnError() throws Exception {
        TestFileServer fileServer = new TestFileServer((ctx, msg) -> {
            ctx.writeAndFlush(new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.NOT_FOUND
            ));
        });

        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null)
                    .verifyError(1, Error.ErrorTypes.FAILED_TO_DOWNLOAD, "Server respond with an error.");

            fileServer.verifyRequest();
            assertFalse(Files.exists(Path.of(holder.serverProperties.getDownloadFolder(), "file.txt")));
        } finally {
            fileServer.close();
        }
    }

    @Test
    void chunkedFileDownload() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        TestFileServer fileServer = TestFileServer.fileLengthResponding(5);
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);

            ChannelHandlerContext ctx = fileServer.verifyRequest();
            ByteBuf content = Unpooled.wrappedBuffer(new byte[]{'a', 'b'});
            ctx.writeAndFlush(new DefaultHttpContent(content));

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "ab" + "\0".repeat(3));

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
            verifyFileContent("file.txt", "ab\0\0\0");

            content = Unpooled.wrappedBuffer(new byte[]{'c', 'd', 'e'});
            ctx.writeAndFlush(new DefaultLastHttpContent(content))
                    .addListener(ChannelFutureListener.CLOSE);

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
            fileServer.close();
        }
    }

    @Test
    void subDirectoriesCreated() throws Exception {
        byte[] fileContent = "This is example file content.".getBytes(StandardCharsets.UTF_8);
        TestFileServer fileServer = TestFileServer.simpleFileServer(fileContent);

        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", "subFolderA/subFolderB");
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(fileContent.length, file.totalBytes());

            fileServer.verifyRequest();
            verifyFileContent("subFolderA/subFolderB/file.txt", "This is example file content.");
        } finally {
            fileServer.close();
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
        webClient.downloadFile("http://localhost:18081/abc", "file.txt", "../")
                .verifyError(11, Error.ErrorTypes.VALIDATION, "Access to this folder is denied!");
        webClient.downloadFile("http://localhost:18081/abc", "file.txt", "/")
                .verifyError(12, Error.ErrorTypes.VALIDATION, "Access to this folder is denied!");
    }

    @Test
    void stopDownloadingFile() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        TestFileServer fileServer = TestFileServer.fileLengthResponding(5);
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);

            ChannelHandlerContext ctx = fileServer.verifyRequest();
            ByteBuf content = Unpooled.wrappedBuffer(new byte[]{'a', 'b'});
            ctx.writeAndFlush(new DefaultHttpContent(content));

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "ab" + "\0".repeat(3));

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
            verifyFileContent("file.txt", "ab\0\0\0");

            file = webClient.stopDownloading(file.id()).parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.PAUSED, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(2, file.downloadedBytes());

            // checking, that the remote connection is closed
            assertWithReties(5, 200, () -> {
                assertFalse(ctx.channel().isOpen());
            });
        } finally {
            fileServer.close();
        }
    }

    @Test
    void stopNonDownloadingFile() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        TestFileServer fileServer = TestFileServer.simpleFileServer(new byte[]{'a', 'b', 'c'});
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);
            fileServer.verifyRequest();

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
            fileServer.close();
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
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);
        TestFileServer fileServer = TestFileServer.fileLengthResponding(5);
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);
            ChannelHandlerContext ctx = fileServer.verifyRequest();
            ByteBuf content = Unpooled.wrappedBuffer(new byte[]{'a', 'b'});
            ctx.writeAndFlush(new DefaultHttpContent(content));

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "ab" + "\0".repeat(3));

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
            verifyFileContent("file.txt", "ab\0\0\0");

            file = webClient.stopDownloading(file.id()).parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.PAUSED, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(2, file.downloadedBytes());

            fileServer.requestHandler(TestFileServer.RequestHandler.contentRangeResponding(4, "bytes=2-5/6"));
            webClient.resumeDownloading(file.id());
            ctx = fileServer.verifyRequest(r -> "bytes=2-".equals(r.headers().get("Range")));

            content = Unpooled.wrappedBuffer(new byte[]{'c', 'd', 'e', 'j'});
            ctx.writeAndFlush(new DefaultLastHttpContent(content))
                    .addListener(ChannelFutureListener.CLOSE);

            file = webClient.parseDownloadFile(2);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(6, file.totalBytes());
            assertEquals(2, file.downloadedBytes());

            assertWithReties(5, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADED, reportedFile.status());
                assertEquals(6, reportedFile.totalBytes());
                assertEquals(6, reportedFile.downloadedBytes());
            });
            verifyFileContent("file.txt", "abcdej");
        } finally {
            fileServer.close();
        }
    }

    @Test
    void resumeDownloadingWithNoContentRangeSupport() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);

        TestFileServer fileServer = TestFileServer.fileLengthResponding(5);
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);
            ChannelHandlerContext ctx = fileServer.verifyRequest();
            ByteBuf content = Unpooled.wrappedBuffer(new byte[]{'a', 'b'});
            ctx.writeAndFlush(new DefaultHttpContent(content));

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "ab" + "\0".repeat(3));

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
            verifyFileContent("file.txt", "ab\0\0\0");

            file = webClient.stopDownloading(file.id()).parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.PAUSED, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(2, file.downloadedBytes());

            fileServer.reset();
            webClient.resumeDownloading(file.id());

            ctx = fileServer.verifyRequest();

            content = Unpooled.wrappedBuffer(new byte[]{'a'});
            ctx.writeAndFlush(new DefaultHttpContent(content)).sync();

            file = webClient.parseDownloadFile(2);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(2, file.downloadedBytes());

            content = Unpooled.wrappedBuffer(new byte[]{'b', 'c'});
            ctx.writeAndFlush(new DefaultHttpContent(content)).sync();

            content = Unpooled.wrappedBuffer(new byte[]{'d', 'e'});
            ctx.writeAndFlush(new DefaultLastHttpContent(content))
                    .addListener(ChannelFutureListener.CLOSE);

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
            fileServer.close();
        }
    }

    @Test
    void resumeNonPausedFile() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);

        TestFileServer fileServer = TestFileServer.simpleFileServer(new byte[]{'a', 'b', 'c'});
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
            fileServer.close();
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
        TestFileServer fileServer = TestFileServer.fileLengthResponding(5);
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);
            ChannelHandlerContext ctx = fileServer.verifyRequest();
            ByteBuf content = Unpooled.wrappedBuffer(new byte[]{'a', 'b'});
            ctx.writeAndFlush(new DefaultHttpContent(content));

            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(5, file.totalBytes());
            assertEquals(0, file.downloadedBytes());

            verifyFileContent("file.txt", "ab" + "\0".repeat(3));

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
            verifyFileContent("file.txt", "ab\0\0\0");

            webClient.deleteFile(file.id()).verifyOk(1);

            // checking, that the remote connection is closed
            assertWithReties(5, 200, () -> {
                assertFalse(ctx.channel().isOpen());
            });

            assertFalse(Files.exists(filePath));
        } finally {
            fileServer.close();
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
        webClient.listFolders("../b")
                .verifyError(4, Error.ErrorTypes.VALIDATION, "Access to this folder is denied!");
        webClient.listFolders("a/../../b")
                .verifyError(5, Error.ErrorTypes.VALIDATION, "Access to this folder is denied!");
        webClient.listFolders("/a")
                .verifyError(6, Error.ErrorTypes.VALIDATION, "Access to this folder is denied!");
        webClient.listFolders("//a")
                .verifyError(7, Error.ErrorTypes.VALIDATION, "Access to this folder is denied!");
    }

    @Test
    void getFilesHistory() throws InterruptedException {
        byte[] fileContent = "This is example file content.".getBytes(StandardCharsets.UTF_8);
        TestFileServer fileServer = TestFileServer.simpleFileServer(fileContent);
        try {
            WebClient webClient = loggedAdminWebClient();

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
            assertEquals(file2.id(), file.id());
            assertEquals("file2.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADED, file.status());
            assertEquals(fileContent.length, file.totalBytes());
            assertEquals(fileContent.length, file.downloadedBytes());
            file = page.content()[1];
            assertEquals(file1.id(), file.id());
            assertEquals("file1.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADED, file.status());
            assertEquals(fileContent.length, file.totalBytes());
            assertEquals(fileContent.length, file.downloadedBytes());

            page = webClient.getFiles(0, 1).parseFilesPage(4);
            assertEquals(2, page.totalElements());
            assertNotNull(page.content());
            assertEquals(1, page.content().length);
            file = page.content()[0];
            assertEquals(file2.id(), file.id());
            assertEquals("file2.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADED, file.status());
            assertEquals(fileContent.length, file.totalBytes());
            assertEquals(fileContent.length, file.downloadedBytes());

            page = webClient.getFiles(1, 1).parseFilesPage(5);
            assertEquals(2, page.totalElements());
            assertNotNull(page.content());
            assertEquals(1, page.content().length);
            file = page.content()[0];
            assertEquals(file1.id(), file.id());
            assertEquals("file1.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADED, file.status());
            assertEquals(fileContent.length, file.totalBytes());
            assertEquals(fileContent.length, file.downloadedBytes());

            page = webClient.getFiles(2, 1).parseFilesPage(6);
            assertEquals(2, page.totalElements());
            assertNotNull(page.content());
            assertEquals(0, page.content().length);
        } finally {
            fileServer.close();
        }
    }

    @Test
    void downloadLargeFile() throws Throwable {
        DownloadingFilesReportWorker reportWorker = new DownloadingFilesReportWorker(holder);

        int memorySize = 64 * 1024 * 1024;
        int fileLength = 3 * memorySize;

        byte[] chunk = new byte[fileLength / 6];
        int i = 0;
        for (int j = 0; j < fileLength / 6 / 8; j++) {
            for (byte k = '0'; k < '8'; k++) {
                chunk[i++] = k;
            }
        }

        TestFileServer fileServer = new TestFileServer((ctx, msg) -> {
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(fileLength));
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            ctx.write(response);

            for (int chunkNo = 0; chunkNo < 6; chunkNo++) {
                ctx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk)));
            }

            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                    .addListener(ChannelFutureListener.CLOSE);
        });
        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "file.txt", null);
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());

            assertWithReties(20, 200, () -> {
                webClient.reset();

                reportWorker.run();
                FilesHistoryReportDTO report = webClient.parseFilesHistoryReport(0);
                assertNotNull(report.files());
                assertEquals(1, report.files().size());
                DownloadFileDTO reportedFile = report.files().getFirst();
                assertEquals("file.txt", reportedFile.name());
                assertEquals(DownloadingFileStatus.DOWNLOADED, reportedFile.status());
            });

            Path path = Path.of(holder.serverProperties.getDownloadFolder(), "file.txt");
            assertEquals(fileLength, path.toFile().length());
            char[] readChunk = new char[8];
            char[] assertChunk = new char[]{ '0', '1', '2', '3', '4', '5', '6', '7' };
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                reader.read(readChunk);
                assertArrayEquals(assertChunk, readChunk);
            }
        } finally {
            fileServer.close();
        }
    }

    @Test
    void downloadFileWithEmptyHeaders() throws Exception {
        TestFileServer fileServer = new TestFileServer((ctx, msg) -> {
            byte[] fileContent = "This is example file content.".getBytes(StandardCharsets.UTF_8);
            ctx.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            ctx.writeAndFlush(new DefaultLastHttpContent(Unpooled.wrappedBuffer(fileContent)))
                    .addListener(ChannelFutureListener.CLOSE);
        });

        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "example file.txt", null);
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("example file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(-1, file.totalBytes());

            fileServer.verifyRequest();
            verifyFileContent("example file.txt", "This is example file content.");
        } finally {
            fileServer.close();
        }
    }

    @Test
    void downloadFileWithEmptyBody() throws Exception {
        TestFileServer fileServer = new TestFileServer((ctx, msg) -> {
            ctx.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            ctx.writeAndFlush(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))
                    .addListener(ChannelFutureListener.CLOSE);
        });

        try {
            WebClient webClient = loggedAdminWebClient();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "example file.txt", null);
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("example file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(-1, file.totalBytes());

            fileServer.verifyRequest();
            verifyFileContent("example file.txt", "");
        } finally {
            fileServer.close();
        }
    }

    private static void verifyFileContent(String fileName, String expectedContent) throws IOException {
        String downloadFolder = holder.serverProperties.getDownloadFolder();
        String content = Files.readString(Path.of(downloadFolder, fileName));
        assertEquals(expectedContent, content);
    }
}