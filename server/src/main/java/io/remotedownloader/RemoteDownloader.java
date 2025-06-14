package io.remotedownloader;

import io.remotedownloader.server.HttpServer;
import io.remotedownloader.worker.DownloadingFilesReportWorker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class RemoteDownloader {
    private static final Logger log = LogManager.getLogger(RemoteDownloader.class);

    public static void main(String[] args) throws InterruptedException {
        log.info("Starting RemoteDownloader application...");

        Holder holder = new Holder();

        HttpServer httpServer = new HttpServer(holder);
        httpServer.start();

        holder.threadPoolsHolder.scheduledThreadPoolExecutor.scheduleAtFixedRate(
                new DownloadingFilesReportWorker(holder),
                1, 1, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                httpServer.stop();
            } catch (Exception e) {
                log.warn("Failed to gracefully stop the HTTP server", e);
            }

            try {
                holder.threadPoolsHolder.close();
            } catch (Exception e) {
                log.warn("Failed to gracefully shutdown thread pools", e);
            }
        }));
    }
}