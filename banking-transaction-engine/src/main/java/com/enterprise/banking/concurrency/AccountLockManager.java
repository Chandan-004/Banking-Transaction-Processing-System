package com.enterprise.banking.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-Safe In-Memory Lock Manager for Banking Accounts.
 *
 * Implements fine-grained synchronization using ReentrantLock and ConcurrentHashMap.
 * Enforces Deterministic Lock Ordering on multi-account operations (transfers)
 * to prevent deadlocks and eliminate race conditions at the application layer.
 */
public class AccountLockManager {

    private static final Logger logger = LoggerFactory.getLogger(AccountLockManager.class);

    // Thread-safe collection holding dedicated locks per account ID
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    /**
     * Gets or creates a ReentrantLock for the specified account ID.
     */
    public ReentrantLock getLock(String accountId) {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        return lockMap.computeIfAbsent(accountId, id -> new ReentrantLock(true)); // fair lock
    }

    /**
     * Executes a supplier function under an exclusive account lock.
     * Guarantees race-condition prevention on single-account updates.
     */
    public <T> T executeWithLock(String accountId, Supplier<T> action) {
        ReentrantLock lock = getLock(accountId);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes a supplier function under deterministic dual-account locks (for transfers).
     * Mathematically eliminates circular wait deadlocks:
     * - Always acquires lock on min(accountA, accountB) first.
     * - Then acquires lock on max(accountA, accountB).
     */
    public <T> T executeWithDualLocks(String accountA, String accountB, Supplier<T> action) {
        Objects.requireNonNull(accountA, "accountA cannot be null");
        Objects.requireNonNull(accountB, "accountB cannot be null");

        if (accountA.equals(accountB)) {
            return executeWithLock(accountA, action);
        }

        // Deterministic Lock Ordering: lowest ID locked first
        String firstId = accountA.compareTo(accountB) < 0 ? accountA : accountB;
        String secondId = accountA.compareTo(accountB) < 0 ? accountB : accountA;

        ReentrantLock firstLock = getLock(firstId);
        ReentrantLock secondLock = getLock(secondId);

        firstLock.lock();
        try {
            secondLock.lock();
            try {
                return action.get();
            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }

    /**
     * Returns the number of currently tracked account locks.
     */
    public int getTrackedLockCount() {
        return lockMap.size();
    }
}
