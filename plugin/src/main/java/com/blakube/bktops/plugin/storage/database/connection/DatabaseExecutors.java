package com.blakube.bktops.plugin.storage.database.connection;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class DatabaseExecutors {

    private static final ThreadFactory DB_THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "BK-Tops-DB-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    };

    public static volatile ExecutorService DB_EXECUTOR = Executors.newFixedThreadPool(1, DB_THREAD_FACTORY);

    public static void init(int threads) {
        DB_EXECUTOR = Executors.newFixedThreadPool(Math.max(1, threads), DB_THREAD_FACTORY);
    }

    public static void awaitPendingTasks() {
        try {
            DB_EXECUTOR.submit(() -> {}).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
        }
    }

    private DatabaseExecutors() {}
}
