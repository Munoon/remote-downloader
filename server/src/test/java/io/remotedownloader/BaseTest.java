package io.remotedownloader;

import io.remotedownloader.model.User;
import io.remotedownloader.server.HttpServer;
import io.remotedownloader.util.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public abstract class BaseTest {
    public static Holder holder;
    public static HttpServer httpServer;
    public static User adminUser;

    @BeforeAll
    static void beforeAll() throws InterruptedException, IOException {
        ServerProperties properties = new ServerProperties();
        properties.setProperty("storage.file", Files.createTempFile("remote-downloader-", "-server.properties").toString());
        properties.setProperty("download.folder", Files.createTempDirectory("remote-downloader-downloads-").toString());
        properties.setProperty("port", "18080");

        holder = new Holder(properties);
        httpServer = new HttpServer(holder);
        httpServer.start();
        adminUser = holder.userDao.createAdmin();
    }

    @AfterAll
    static void afterAll() throws IOException {
        WebClient.closeAllClients();

        httpServer.stop();
        holder.threadPoolsHolder.close();
        holder.transportTypeHolder.close();

        Files.deleteIfExists(Path.of(holder.serverProperties.getProperty("storage.file")));

        try (Stream<Path> paths = Files.walk(Path.of(holder.serverProperties.getProperty("download.folder")))) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
