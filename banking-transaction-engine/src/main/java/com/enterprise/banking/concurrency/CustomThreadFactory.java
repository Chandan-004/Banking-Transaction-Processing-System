package com.enterprise.banking.concurrency;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom ThreadFactory that generates named, identifiable worker threads.
 * Thread names (e.g. txn-worker-1, txn-worker-2) propagate into SLF4J MDC
 * and the database TRANSACTION_AUDIT table for thread tracking.
 */
public class CustomThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public CustomThreadFactory(String prefix) {
        this.prefix = prefix != null ? prefix : "txn-worker";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, prefix + "-" + threadNumber.getAndIncrement());
        thread.setDaemon(false);
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }
        return thread;
    }
}
