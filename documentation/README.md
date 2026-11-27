# Banking Transaction Processing System — Project Documentation

**Current Stage:** Phase 3 (Days 8–11) — Concurrency, Thread Lifecycle & ExecutorService Implementation  
**Last Updated:** Current  

---

## 1. Project Overview & Objective

This project is a high-performance, multi-threaded **Banking Transaction Processing Engine** built using **Pure Core Java (Java 21)** and **Plain JDBC** to process high-volume financial operations (Deposit, Withdrawal, and Account-to-Account Transfer) reliably under concurrent load.

> [!IMPORTANT]
> **Strict Architectural Constraint: NO SPRING BOOT.**  
> Spring Boot is **strictly not used** anywhere in this project. The system is designed and built with **Pure Core Java (Java 21)**, native **JDBC (`java.sql`)**, standard connection pooling (HikariCP), standard concurrency primitives (`java.util.concurrent`), **SLF4J + Logback**, and **JUnit 5**.

### Key System Goals (From Assignment Specification):
* **Consistency:** Explicit database transaction boundaries (`connection.setAutoCommit(false)`, `commit()`, `rollback()`) to prevent lost updates and negative balances.
* **Idempotency:** Prevent duplicate financial impact when identical transaction IDs are received using application gates and database constraints.
* **Auditability:** Complete transaction history and failure reasons persisted in the database with structured logging (MDC).
* **Concurrency & Load Benchmark:** Custom thread-pool engine (`ExecutorService`) processing 50,000+ simulated transactions across 1, 5, 10, and 20 worker threads.
* **Database & SQL Proficiency:** Normalized relational schema, constraints, indexes, and 5 mandatory reporting queries with `EXPLAIN` analysis.

---

## 2. 📂 Project Documentation Index

| Document | Description | Status |
|---|---|---|
| [**ARCHITECTURE_AND_DESIGN.md**](file:///c:/Users/chand/OneDrive/Desktop/banking-transaction-engine/documentation/ARCHITECTURE_AND_DESIGN.md) | **Complete System Architecture, ERD, UML & Design Patterns**<br>• System Reference Architecture (5-tier decoupled pipeline)<br>• Entity-Relationship Diagram (ERD) with 5 normalized tables & constraints<br>• UML Class Diagram (Pure Java records, DAOs, Services, Strategies)<br>• Design Patterns Applied (Singleton, DAO, Pessimistic Locking, Deterministic Order, Value Object) | **Active Baseline** |

---

## 3. Current Project Setup & Technology Stack

### 3.1 Build & Runtime Environment
* **Language & Runtime:** Java 21 (`<java.version>21</java.version>`)
* **Build Tool:** Apache Maven (with standard `pom.xml`)
* **Architecture Style:** Pure Core Java / Clean Modular Architecture (No Spring Boot framework dependencies)
* **Concurrency Layer:** `java.util.concurrent.ThreadPoolExecutor`, `CompletableFuture`, bounded `ArrayBlockingQueue`, custom `ThreadFactory`
* **Database:** MySQL / PostgreSQL via standard JDBC (`java.sql.Connection`, `PreparedStatement`)
* **Connection Pooling:** HikariCP (`com.zaxxer:HikariCP`)
* **Logging:** SLF4J API (`org.slf4j:slf4j-api`) + Logback Classic (`ch.qos.logback:logback-classic`)
* **Testing:** JUnit 5 (`org.junit.jupiter:junit-jupiter`), AssertJ, and H2 (for isolated in-memory test execution)

---

## 4. Project Directory & Package Structure

```
banking-transaction-engine/
├── banking-transaction-engine/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/enterprise/banking/
│   │   │   │   ├── concurrency/
│   │   │   │   │   ├── AccountLockManager.java              # Fine-grained in-memory ReentrantLock & deterministic dual locking
│   │   │   │   │   ├── ConcurrentTransactionProcessor.java  # Bounded ThreadPoolExecutor, Callable, Future & CompletableFuture
│   │   │   │   │   ├── CustomThreadFactory.java             # Thread lifecycle & named worker generator (txn-worker-n)
│   │   │   │   │   └── TransactionTask.java                 # Callable<TransactionResult> task wrapper
│   │   │   │   ├── config/
│   │   │   │   │   ├── DatabaseConnectionManager.java       # Singleton HikariCP connection pool
│   │   │   │   │   └── EnvLoader.java                       # Pure Java .env & environment variable loader
│   │   │   │   ├── dao/
│   │   │   │   │   ├── AccountDAO.java                      # Interface for Account JDBC operations & row locks
│   │   │   │   │   ├── TransactionDAO.java                  # Interface for Txn, Audit & Failure logging
│   │   │   │   │   ├── CustomerDAO.java                     # Interface for Customer master access
│   │   │   │   │   └── impl/
│   │   │   │   │       ├── AccountDAOImpl.java              # JDBC queries: SELECT FOR UPDATE, UPDATE balance
│   │   │   │   │       ├── TransactionDAOImpl.java          # JDBC queries: isDuplicate, saveAudit, saveFailure
│   │   │   │   │       └── CustomerDAOImpl.java             # JDBC queries: findById, save
│   │   │   │   ├── dto/
│   │   │   │   │   ├── TransactionRequest.java              # Immutable Java 21 record for incoming requests
│   │   │   │   │   └── TransactionResult.java               # Immutable Java 21 record for responses
│   │   │   │   ├── exception/
│   │   │   │   │   ├── BankingException.java                # Base domain runtime exception
│   │   │   │   │   ├── InsufficientBalanceException.java    # Balance sufficiency violation
│   │   │   │   │   ├── AccountNotFoundException.java        # Missing account error
│   │   │   │   │   ├── AccountInactiveException.java        # Suspended / closed account error
│   │   │   │   │   ├── DuplicateTransactionException.java   # Idempotency duplicate violation
│   │   │   │   │   └── InvalidTransactionException.java     # Input validation rule violation
│   │   │   │   ├── model/
│   │   │   │   │   ├── Account.java                         # Account domain entity with debit/credit logic
│   │   │   │   │   ├── AccountStatus.java                   # ACTIVE, SUSPENDED, CLOSED enum
│   │   │   │   │   ├── Customer.java                        # Customer entity
│   │   │   │   │   ├── ErrorCategory.java                   # Standard error categories enum
│   │   │   │   │   ├── FailedTransaction.java               # Failure log diagnostic entity
│   │   │   │   │   ├── TransactionAudit.java                # Financial before/after balance audit entity
│   │   │   │   │   ├── TransactionRecord.java               # Transaction row entity
│   │   │   │   │   ├── TransactionStatus.java               # SUCCESS, FAILED, REJECTED enum
│   │   │   │   │   └── TransactionType.java                 # DEPOSIT, WITHDRAWAL, TRANSFER enum
│   │   │   │   ├── service/
│   │   │   │   │   └── BankingTransactionEngine.java        # Core Service: Deposit, Withdraw, Transfer & Locking
│   │   │   │   └── BankingTransactionEngineApplication.java  # Core Java entry point
│   │   │   └── resources/
│   │   │       ├── database.properties                       # HikariCP JDBC connection settings
│   │   │       ├── logback.xml                               # SLF4J / Logback configuration
│   │   │       └── schema.sql                                # Normalized 5-table MySQL DDL with constraints
│   │   └── test/
│   │       └── java/com/enterprise/banking/
│   │           ├── AccountTest.java                          # Account unit tests (7 tests)
│   │           ├── BankingTransactionEngineApplicationTests.java # Sanity test (1 test)
│   │           ├── BankingTransactionEngineTest.java         # Money movement integration tests (10 tests)
│   │           ├── CallableFutureAndLockingTest.java         # Callable, Future, AccountLockManager & dual locks (4 tests)
│   │           ├── EnvLoaderTest.java                        # Environment loader tests (1 test)
│   │           ├── ExceptionHierarchyTest.java               # Exception hierarchy tests (1 test)
│   │           ├── MandatoryConcurrencyTest.java             # Concurrency proof & 20-thread tests (3 tests)
│   │           └── TransactionDtoTest.java                   # DTO record tests (2 tests)
│   ├── .env                                                  # Ignored active secrets
│   ├── .env.example                                          # Environment template
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml                                               # Pure Java Maven dependencies
└── documentation/
    ├── README.md                                             # Project setup, constraints & package catalog
    └── ARCHITECTURE_AND_DESIGN.md                           # System Architecture, ERD, UML & Patterns
```

---

## 5. Milestone Progress Log

* **Phase 1 (Days 1–3) [Completed]:** Requirements decomposition, 5-table normalized schema, Core Java architecture without Spring Boot, domain models, immutable records, custom exceptions, DAO contracts, `.env` integration, and HikariCP connection pool setup.
* **Phase 2 (Days 4–7) [Completed]:** Customer & Account models, plain JDBC DAO implementations, core money movement (Deposit, Withdraw, Transfer), row-level pessimistic locking (`SELECT ... FOR UPDATE`), and deterministic lock ordering.
* **Phase 3 (Days 8–11) [Completed Today]:**
  1. **Thread Lifecycle & Naming:** Implemented `CustomThreadFactory` to assign identifiable names (`txn-worker-1`, `txn-worker-2`) propagated into SLF4J MDC and `TRANSACTION_AUDIT`.
  2. **Bounded Execution Layer:** Implemented `ConcurrentTransactionProcessor` wrapping `ThreadPoolExecutor` with a bounded queue (`ArrayBlockingQueue`) and `CallerRunsPolicy` backpressure.
  3. **Callable & Future Submission:** Created `TransactionTask` implementing `Callable<TransactionResult>` and exposed `submit(Callable)`, `submitCallable(TransactionRequest)`, and `invokeAll(List<Callable>)` returning typed `Future` instances.
  4. **In-Memory Synchronization & Locking (`AccountLockManager`):** Fine-grained per-account fair `ReentrantLock` stored in `ConcurrentHashMap`. Prevents race conditions before hitting the database.
  5. **Deterministic Dual Locking:** Implemented `executeWithDualLocks(accountA, accountB)` enforcing canonical ordering (`min -> max`) to eliminate circular wait deadlocks on reciprocal transfers.
  6. **Mandatory Concurrency Scenario Test:** Proven that an account with ₹10,000 opening balance subjected to simultaneous ₹7,000 and ₹5,000 withdrawals allows exactly one to succeed and prevents negative balances.
  7. **20-Thread Concurrent Stress Test:** Verified 20 concurrent threads hammering an account with zero balance corruption and zero lost updates.
  8. **Deadlock Freedom (20 Workers):** Verified 20 concurrent threads running cross-transfers ($A \rightarrow B$ and $B \rightarrow A$) execute cleanly without deadlocks.
  9. **Comprehensive Test Suite:** **29 automated tests passing cleanly (`BUILD SUCCESS`)** across 8 test suites.
