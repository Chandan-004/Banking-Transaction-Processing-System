package com.enterprise.banking.concurrency;

import com.enterprise.banking.dto.TransactionRequest;
import com.enterprise.banking.dto.TransactionResult;
import com.enterprise.banking.service.BankingTransactionEngine;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Task representation of a banking transaction implementing Callable.
 * Enables execution via ExecutorService and result retrieval via Future.
 */
public class TransactionTask implements Callable<TransactionResult> {

    private final BankingTransactionEngine engine;
    private final TransactionRequest request;

    public TransactionTask(BankingTransactionEngine engine, TransactionRequest request) {
        this.engine = Objects.requireNonNull(engine, "BankingTransactionEngine cannot be null");
        this.request = Objects.requireNonNull(request, "TransactionRequest cannot be null");
    }

    @Override
    public TransactionResult call() {
        return engine.process(request);
    }

    public TransactionRequest getRequest() {
        return request;
    }
}
