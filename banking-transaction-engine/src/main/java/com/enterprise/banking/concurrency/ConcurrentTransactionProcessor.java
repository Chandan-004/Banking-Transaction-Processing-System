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
 * - Callable and Future submission models.
 * - Backpressure via bounded queue and CallerRunsPolicy.
 * - In-memory synchronization & locking via AccountLockManager to prevent race conditions.
 * - Clean lifecycle management (graceful shutdown, await termination).
 */
public class ConcurrentTransactionProcessor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentTransactionProcessor.class);

    private final BankingTransactionEngine engine;
    private final ThreadPoolExecutor executor;
    private final AccountLockManager lockManager;
    private final int workerCount;

    public ConcurrentTransactionProcessor(BankingTransactionEngine engine, int workerCount, int queueCapacity) {
        this(engine, workerCount, queueCapacity, new AccountLockManager());
    }

    public ConcurrentTransactionProcessor(BankingTransactionEngine engine, int workerCount, int queueCapacity, AccountLockManager lockManager) {
        this.engine = Objects.requireNonNull(engine, "BankingTransactionEngine cannot be null");
        this.lockManager = Objects.requireNonNull(lockManager, "AccountLockManager cannot be null");
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

    // =========================================================================
    // Future & CompletableFuture Submission APIs
    // =========================================================================

    /**
     * Submits a transaction as an explicit Callable, returning a Future.
     */
    public Future<TransactionResult> submit(Callable<TransactionResult> task) {
        return executor.submit(task);
    }

    /**
     * Submits a TransactionRequest wrapped in a synchronized TransactionTask Callable,
     * returning a Future<TransactionResult>.
     */
    public Future<TransactionResult> submitCallable(TransactionRequest request) {
        Callable<TransactionResult> task = wrapWithLocking(request);
        return executor.submit(task);
    }

    /**
     * Submits a single transaction asynchronously using CompletableFuture.
     */
    public CompletableFuture<TransactionResult> submit(TransactionRequest request) {
        Callable<TransactionResult> task = wrapWithLocking(request);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                return TransactionResult.failed(request.transactionId(), e.getMessage(), 0);
            }
        }, executor);
    }

    /**
     * Executes a list of Callables via ExecutorService.invokeAll, waiting for all Futures.
     */
    public List<Future<TransactionResult>> invokeAll(List<Callable<TransactionResult>> tasks) throws InterruptedException {
        return executor.invokeAll(tasks);
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

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<TransactionResult> results = new ArrayList<>(futures.size());
        for (CompletableFuture<TransactionResult> future : futures) {
            results.add(future.join());
        }
        return results;
    }

    // =========================================================================
    // Synchronization & Locking Helper
    // =========================================================================

    /**
     * Wraps a transaction request with in-memory synchronization via AccountLockManager.
     * Prevents thread contention and race conditions at the application layer.
     */
    private Callable<TransactionResult> wrapWithLocking(TransactionRequest req) {
        return switch (req.type()) {
            case DEPOSIT -> {
                String targetId = req.destinationAccount() != null ? req.destinationAccount() : req.sourceAccount();
                yield () -> lockManager.executeWithLock(targetId, () -> engine.process(req));
            }
            case WITHDRAWAL -> () -> lockManager.executeWithLock(req.sourceAccount(), () -> engine.process(req));
            case TRANSFER -> () -> lockManager.executeWithDualLocks(req.sourceAccount(), req.destinationAccount(), () -> engine.process(req));
        };
    }

    // =========================================================================
    // Lifecycle Management
    // =========================================================================

    public void shutdown() {
        logger.info("Initiating graceful shutdown of ConcurrentTransactionProcessor...");
        executor.shutdown();
    }

    public List<Runnable> shutdownNow() {
        logger.warn("Initiating immediate shutdownNow of ConcurrentTransactionProcessor...");
        return executor.shutdownNow();
    }

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

    public AccountLockManager getLockManager() {
        return lockManager;
    }

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
