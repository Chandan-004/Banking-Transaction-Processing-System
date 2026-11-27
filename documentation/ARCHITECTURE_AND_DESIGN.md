# System Architecture, ERD & UML Design
## Multi-Threaded Banking Transaction Processing System

**Project:** Banking Transaction Processing System  
**Architecture:** Pure Core Java 21 | Plain JDBC | HikariCP  
**Style:** Simple Box-and-Arrow Diagrams
---

## 1. System Architecture Diagram

```
+---------------------------------------------------------------------------------+
|                              1. INCOMING CHANNELS                               |
|        [ ATM ]   [ Internet Banking ]   [ Mobile App ]   [ Branch ]             |
|              \              |                  |             /                  |
|               v             v                  v            v                   |
|                   [ 50,000+ Transactions Workload Simulator ]                   |
+---------------------------------------------------------------------------------+
                                        |
                                        v
+---------------------------------------------------------------------------------+
|                       2. BOUNDED CONCURRENCY LAYER                              |
|           [ ThreadPoolExecutor (1 / 5 / 10 / 20 Worker Threads) ]               |
|                    Bounded Queue (ArrayBlockingQueue)                           |
+---------------------------------------------------------------------------------+
                                        |
                                        v
+---------------------------------------------------------------------------------+
|                       3. TRANSACTION ENGINE (7-STEP LIFECYCLE)                  |
|                                                                                 |
|  [Step 1: Receive]                                                              |
|         |                                                                       |
|  [Step 2: Validation Gate] -----(Invalid)----> [REJECTED (InvalidTxnException)]|
|         |                                                                       |
|  [Step 3: Idempotency Check] --(Duplicate)---> [REJECTED / Cached Response]     |
|         |                                                                       |
|  [Step 4: Begin Txn + Lock] ---> Deterministic Order: min(AccA, AccB)           |
|         |                                                                       |
|  [Step 5: Apply Operation] ----(Low Balance)-> [FAILED (InsufficientBalance)]   |
|         |                                                                       |
|  [Step 6: Audit + Commit] -----(DB Commit)                                      |
|         |                                                                       |
|  [Step 7: Respond] ------------> [SUCCESS]                                      |
+---------------------------------------------------------------------------------+
                                        |
                                        v
+---------------------------------------------------------------------------------+
|                       4. DATA ACCESS LAYER (PLAIN JDBC)                         |
|                                                                                 |
|        [ AccountDAO ]                       [ TransactionDAO ]                  |
|     - findByIdForUpdate()                - saveTransaction()                    |
|     - updateBalance()                    - saveAudit() & saveFailure()          |
|                 \                               /                               |
|                  v                             v                                |
|             [ DatabaseConnectionManager (HikariCP Connection Pool) ]            |
+---------------------------------------------------------------------------------+
                                        |
                                        v
+---------------------------------------------------------------------------------+
|                        5. DATABASE STORAGE (MySQL)                              |
|                                                                                 |
|     [ customer ]  <--(1:N)--  [ account ]  <--(source/dest)--  [ transaction ]  |
|                               (balance >= 0)                   (UNIQUE txId)    |
|                                                                       |         |
|                                               +-----------------------+         |
|                                               |                       |         |
|                                               v                       v         |
|                                     [ transaction_audit ]   [ failed_txn ]      |
+---------------------------------------------------------------------------------+
```

---

## 2. Simple Entity-Relationship Diagram (ERD)

```
+-----------------------+              +---------------------------+
|       CUSTOMER        |              |          ACCOUNT          |
+-----------------------+              +---------------------------+
| PK customer_id        |<---(1:N)-----| PK account_id             |
|    full_name          |              | FK customer_id            |
|    email              |              |    balance (CHECK >= 0)   |
|    phone_number       |              |    currency               |
|    created_at         |              |    status (ACTIVE, etc.)  |
+-----------------------+              |    version                |
                                       |    created_at, updated_at |
                                       +---------------------------+
                                                     |
                                                     | (source / dest)
                                                     v
                                       +---------------------------+
                                       |        TRANSACTION        |
                                       +---------------------------+
                                       | PK transaction_id (UNIQUE)|
                                       | FK source_account_id      |
                                       | FK destination_account_id |
                                       |    transaction_type       |
                                       |    amount (CHECK > 0)     |
                                       |    status                 |
                                       |    created_at             |
                                       +---------------------------+
                                                     |
                                   +-----------------+-----------------+
                                   | (audit log)                       | (failure details)
                                   v                                   v
+------------------------------------+   +------------------------------------+
|         TRANSACTION_AUDIT          |   |         FAILED_TRANSACTION         |
+------------------------------------+   +------------------------------------+
| PK audit_id                        |   | PK failure_id                      |
| FK transaction_id                  |   | FK transaction_id                  |
|    source_balance_before           |   |    error_category                  |
|    source_balance_after            |   |    failure_reason                  |
|    dest_balance_before             |   |    failed_at                       |
|    dest_balance_after              |   +------------------------------------+
|    execution_thread                |
|    duration_ms                     |
|    audited_at                      |
+------------------------------------+
```

---

## 3. Simple UML Class Diagram

```
+-------------------------------------------------------+
|                 TransactionRequest                    |
+-------------------------------------------------------+
| + transactionId: String                               |
| + sourceAccount: String                               |
| + destinationAccount: String                          |
| + amount: BigDecimal                                  |
| + type: TransactionType (DEPOSIT/WITHDRAW/TRANSFER)   |
+-------------------------------------------------------+
                           |
                           v
+-------------------------------------------------------+
|              BankingTransactionEngine                 |
+-------------------------------------------------------+
| - accountDao: AccountDAO                              |
| - transactionDao: TransactionDAO                      |
| - connectionManager: DatabaseConnectionManager        |
+-------------------------------------------------------+
| + process(TransactionRequest): TransactionResult      |
| - processDeposit(Connection, TransactionRequest)      |
| - processWithdrawal(Connection, TransactionRequest)   |
| - processTransfer(Connection, TransactionRequest)     |
+-------------------------------------------------------+
               |                         |
               v                         v
+-----------------------------+   +-------------------------------+
|         AccountDAO          |   |        TransactionDAO         |
+-----------------------------+   +-------------------------------+
| + findByIdForUpdate(conn)   |   | + saveTransaction(conn)       |
| + updateBalance(conn)       |   | + saveAudit(conn)             |
| + findById(conn)            |   | + saveFailure(conn)           |
+-----------------------------+   | + isDuplicate(conn, txId)     |
               \                  +-------------------------------+
                \                               /
                 v                             v
+-------------------------------------------------------+
|              DatabaseConnectionManager                |
+-------------------------------------------------------+
| - dataSource: HikariDataSource                        |
| - INSTANCE: DatabaseConnectionManager                 |
+-------------------------------------------------------+
| + getInstance(): DatabaseConnectionManager           |
| + getConnection(): Connection                         |
+-------------------------------------------------------+
```

### UML Classes: Where & Why Used (Short & Crisp)

| UML Class / Component | Where It Is Used | Why It Is Used |
|---|---|---|
| **`TransactionRequest`** | Ingestion & Controller layer | Immutable Java 21 `record` to safely transfer transaction inputs across threads without race conditions. |
| **`TransactionResult`** | Engine output & API response | Standard response object returning `SUCCESS`, `FAILED`, or `REJECTED` status, new balances, and execution time. |
| **`TransactionTask`** | Concurrency / Task layer | `Callable<TransactionResult>` encapsulating the transaction operation for executor thread execution and `Future` return. |
| **`ConcurrentTransactionProcessor`** | Concurrency / Thread Pool layer | Bounded `ThreadPoolExecutor` managing worker threads (1, 5, 10, 20), queue backpressure, `Callable`/`Future`/`CompletableFuture` APIs, and shutdown lifecycle. |
| **`AccountLockManager`** | Concurrency / Synchronization layer | In-memory fine-grained `ReentrantLock` manager with `ConcurrentHashMap` providing single-account locking and deterministic dual locking for transfers. |
| **`CustomThreadFactory`** | Concurrency / Thread Factory | Generates named, daemonless worker threads (`txn-worker-n`) with uncaught exception logging. |
| **`Account`** | Domain Entity | Encapsulates account properties (`balance`, `status`) and ensures balance calculations are centralized. |
| **`BankingTransactionEngine`** | Service Layer | Main orchestrator that runs the 7-step transaction sequence, handles deterministic lock ordering, and coordinates rollbacks. |
| **`AccountDAO`** | Data Access Layer | Executes plain JDBC queries (`SELECT ... FOR UPDATE`) to lock and update account rows. |
| **`TransactionDAO`** | Data Access Layer | Handles database idempotency checks (`isDuplicate`) and persists audit records and failure logs. |
| **`DatabaseConnectionManager`** | Infrastructure Layer | Singleton holding a single HikariCP connection pool so worker threads don't leak database connections. |
| **Custom Exceptions** | Domain Error Handling | Specific exceptions (`InsufficientBalanceException`, `DuplicateTransactionException`) for clean rollbacks and exact audit reasons. |

---

## 4. Design Patterns & Concurrency Primitives: Where & Why (Short & Crisp)

* **Command / Task Pattern (`TransactionTask` implementing `Callable<TransactionResult>`):**
  * *Where:* Concurrency task submission.
  * *Why:* Encapsulates banking operations into typed, callable units executed asynchronously via `ExecutorService` and retrieved via `Future.get()`.

* **Bounded Thread Pool & Backpressure (`ThreadPoolExecutor`):**
  * *Where:* `ConcurrentTransactionProcessor`.
  * *Why:* Caps concurrency to 1, 5, 10, or 20 worker threads using `ArrayBlockingQueue` and `CallerRunsPolicy` to prevent Out-Of-Memory (OOM) failures under 50,000+ transaction loads.

* **In-Memory Fine-Grained Locking (`AccountLockManager`):**
  * *Where:* Application synchronization before database interaction.
  * *Why:* Uses per-account fair `ReentrantLock` instances stored in a `ConcurrentHashMap` to serialize concurrent requests targeting the same account. Completely eliminates race conditions and reduces database lock contention.

* **Deterministic Lock Ordering (`min(ID) -> max(ID)`):**
  * *Where:* `AccountLockManager.executeWithDualLocks()` and `BankingTransactionEngine.processTransfer()`.
  * *Why:* When transferring funds between two accounts, always acquires the lock on the lower account ID first, then the higher account ID. Mathematically eliminates circular wait conditions and guarantees zero deadlocks.

* **Pessimistic Database Locking (`SELECT ... FOR UPDATE`):**
  * *Where:* `AccountDAO.findByIdForUpdate()`.
  * *Why:* Locks account rows at the database level so concurrent threads or multi-instance deployments cannot create dirty reads, lost updates, or negative balances.

* **Singleton Pattern (`DatabaseConnectionManager`):**
  * *Where:* HikariCP connection pool.
  * *Why:* Only 1 database pool exists across all worker threads to prevent socket and connection leaks.

* **DAO Pattern (`AccountDAO`, `TransactionDAO`):**
  * *Where:* Data access layer.
  * *Why:* Decouples business transaction logic from raw SQL `PreparedStatement` executions.

* **Value Object Pattern (`TransactionRequest`, `TransactionResult`):**
  * *Where:* DTOs passed between threads and layers.
  * *Why:* Java `record`s are deeply immutable and inherently thread-safe across concurrent threads.
