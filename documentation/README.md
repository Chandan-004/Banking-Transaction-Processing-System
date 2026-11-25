# Banking Transaction Processing System — Project Documentation

**Current Stage:** Phase 2 (Days 4–7) — Core Money Movement, JDBC & SQL Implementation  
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
│   │           ├── EnvLoaderTest.java                        # Environment loader tests (1 test)
│   │           ├── ExceptionHierarchyTest.java               # Exception hierarchy tests (1 test)
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
* **Phase 2 (Days 4–7) [Completed Today]:**
  1. **Customer & Account Models:** Fully implemented with defensive validation and overdraft prevention.
  2. **Plain JDBC DAO Implementations:** `CustomerDAOImpl`, `AccountDAOImpl`, and `TransactionDAOImpl` utilizing parameterized `PreparedStatement`s.
  3. **Core Money Movement:** Implemented `deposit()`, `withdraw()`, and `transfer()` in `BankingTransactionEngine` enforcing the 7-step transaction lifecycle.
  4. **Pessimistic Row Locking:** Implemented `SELECT ... FOR UPDATE` queries in `AccountDAOImpl`.
  5. **Deterministic Lock Ordering:** Enforced `min(accA, accB) -> max(accA, accB)` lock acquisition order in transfers to prevent circular deadlocks.
  6. **Idempotency Gate & Audit Logging:** Implemented duplicate transaction prevention and balance audit persistence (`TRANSACTION_AUDIT` & `FAILED_TRANSACTION`).
  7. **Comprehensive Test Suite:** **22 automated unit and integration tests passing cleanly (`BUILD SUCCESS`)**.
