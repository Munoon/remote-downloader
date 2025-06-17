package io.remotedownloader.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ThreadPoolsHolder {
    private static final Logger log = LogManager.getLogger(ThreadPoolsHolder.class);
    public final ScheduledExecutorService scheduledThreadPoolExecutor =
            Executors.newSingleThreadScheduledExecutor(threadFactory("Scheduled-Jobs"));

    public final ScheduledExecutorService storageThreadPoolExecutor =
            Executors.newSingleThreadScheduledExecutor(threadFactory("Storage"));

    public final ExecutorService blockingTasksExecutor =
            Executors.newSingleThreadExecutor(threadFactory("Blocking-Task-Executor"));

    public void close() {
        scheduledThreadPoolExecutor.shutdown();
        blockingTasksExecutor.shutdown();
        try {
            storageThreadPoolExecutor.shutdown();
            storageThreadPoolExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to gracefully shutdown storage thread pool", e);
        }
    }

    private static ThreadFactory threadFactory(String threadName) {
        return new ThreadFactory() {
            private int threadNumber = 0;

            @Override
            public Thread newThread(@NotNull Runnable r) {
                return new Thread(r, threadName + "-" + threadNumber++);
            }
        };
    }
}
