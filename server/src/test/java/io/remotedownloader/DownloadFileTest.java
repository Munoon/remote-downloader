package io.remotedownloader;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.util.WebClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static io.remotedownloader.util.WebClient.loggedAdminWebClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
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
            webClient.reset();

            webClient.downloadFile("http://127.0.0.1:18081/example-file.txt", "example file.txt", null);
            DownloadFileDTO file = webClient.parseDownloadFile(1);
            assertEquals("example file.txt", file.name());
            assertEquals(DownloadingFileStatus.DOWNLOADING, file.status());
            assertEquals(fileContent.length, file.totalBytes());

            verify(downloadFileHandler).handle(any());
        } finally {
            fileServer.stop(0);
        }
    }
}
