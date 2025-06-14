package io.remotedownloader.dao;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class ThreadPoolsHolder {
    public final ScheduledExecutorService scheduledThreadPoolExecutor =
            Executors.newSingleThreadScheduledExecutor(threadFactory("Scheduled-Jobs"));

    public final ScheduledExecutorService storageThreadPoolExecutor =
            Executors.newSingleThreadScheduledExecutor(threadFactory("Storage"));

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
