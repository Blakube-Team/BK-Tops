package com.blakube.bktops.plugin.storage.database.connection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class DatabaseExecutors {

    private static final int THREADS = 4;

    private static final ThreadFactory DB_THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "BK-Tops-DB-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    };

    public static final ExecutorService DB_EXECUTOR = Executors.newFixedThreadPool(THREADS, DB_THREAD_FACTORY);

    private DatabaseExecutors() {}
}
