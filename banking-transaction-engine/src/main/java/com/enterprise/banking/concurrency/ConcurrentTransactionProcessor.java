package com.enterprise.banking.concurrency;

import com.enterprise.banking.dto.TransactionRequest;
import com.enterprise.banking.dto.TransactionResult;
import com.enterprise.banking.service.BankingTransactionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Bounded Concurrency Processor for high-throughput banking transactions.
 * Uses a ThreadPoolExecutor backed by an ArrayBlockingQueue.
 *
 * Implements the Bounded Execution Layer mandated in PDF Reference Architecture:
 * - Configurable worker count (1, 5, 10, 20 threads).
 * - Backpressure via bounded queue and CallerRunsPolicy.
 * - Clean lifecycle management (graceful shutdown, await termination).
 */
public class ConcurrentTransactionProcessor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentTransactionProcessor.class);

    private final BankingTransactionEngine engine;
    private final ThreadPoolExecutor executor;
    private final int workerCount;

    /**
     * Constructs a processor with specified worker thread count and queue capacity.
     *
     * @param engine        the core transaction engine
     * @param workerCount   number of worker threads (e.g. 1, 5, 10, 20)
     * @param queueCapacity capacity of the bounded blocking queue
     */
    public ConcurrentTransactionProcessor(BankingTransactionEngine engine, int workerCount, int queueCapacity) {
        this.engine = Objects.requireNonNull(engine, "BankingTransactionEngine cannot be null");
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count must be strictly positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be strictly positive");
        }

        this.workerCount = workerCount;

        // Bounded ThreadPoolExecutor with CallerRunsPolicy to prevent OOM
        this.executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new CustomThreadFactory("txn-worker"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("Initialized ConcurrentTransactionProcessor [workers={}, queueCapacity={}]",
                workerCount, queueCapacity);
    }

    /**
     * Submits a single transaction asynchronously.
     * Returns a CompletableFuture holding the TransactionResult.
     */
    public CompletableFuture<TransactionResult> submit(TransactionRequest request) {
        return CompletableFuture.supplyAsync(() -> engine.process(request), executor);
    }

    /**
     * Processes a batch of transactions concurrently and waits for all to complete.
     */
    public List<TransactionResult> processBatch(List<TransactionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<TransactionResult>> futures = new ArrayList<>(requests.size());
        for (TransactionRequest request : requests) {
            futures.add(submit(request));
        }

        // Wait for all worker threads to finish their assigned tasks
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<TransactionResult> results = new ArrayList<>(futures.size());
        for (CompletableFuture<TransactionResult> future : futures) {
            results.add(future.join());
        }
        return results;
    }

    // =========================================================================
    // Lifecycle Management
    // =========================================================================

    /**
     * Initiates an orderly shutdown where previously submitted tasks are executed.
     */
    public void shutdown() {
        logger.info("Initiating graceful shutdown of ConcurrentTransactionProcessor...");
        executor.shutdown();
    }

    /**
     * Attempts to stop all actively executing tasks and halts task processing.
     */
    public List<Runnable> shutdownNow() {
        logger.warn("Initiating immediate shutdownNow of ConcurrentTransactionProcessor...");
        return executor.shutdownNow();
    }

    /**
     * Blocks until all tasks have completed execution after a shutdown request,
     * or the timeout occurs, or the current thread is interrupted.
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(5, TimeUnit.SECONDS)) {
                shutdownNow();
            }
        } catch (InterruptedException e) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // Runtime Monitoring & Metrics
    // =========================================================================

    public int getWorkerCount() {
        return workerCount;
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }
}
