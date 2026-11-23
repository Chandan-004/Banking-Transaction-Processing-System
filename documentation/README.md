# Banking Transaction Processing System — Project Documentation

**Current Stage:** Day 3 — Core Java Architecture & Baseline Implementation  
**Last Updated:** Day 3  

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
* **Testing:** JUnit 5 (`org.junit.jupiter:junit-jupiter`)

---

## 4. Project Directory & Package Structure (Day 3 Status)

```
banking-transaction-engine/
├── banking-transaction-engine/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/enterprise/banking/
│   │   │   │   ├── config/
│   │   │   │   │   └── DatabaseConnectionManager.java       # Singleton HikariCP connection pool
│   │   │   │   ├── dao/
│   │   │   │   │   ├── AccountDAO.java                      # Interface for Account JDBC operations & row locks
│   │   │   │   │   ├── TransactionDAO.java                  # Interface for Txn, Audit & Failure logging
│   │   │   │   │   └── CustomerDAO.java                     # Interface for Customer master access
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
│   │   │   │   └── BankingTransactionEngineApplication.java  # Core Java entry point
│   │   │   └── resources/
│   │   │       ├── database.properties                       # HikariCP JDBC connection settings
│   │   │       ├── logback.xml                               # SLF4J / Logback configuration
│   │   │       └── schema.sql                                # Normalized 5-table MySQL DDL with constraints
│   │   └── test/
│   │       └── java/com/enterprise/banking/
│   │           └── BankingTransactionEngineApplicationTests.java # JUnit 5 test
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml                                               # Pure Java Maven dependencies
└── documentation/
    ├── README.md                                             # Project setup, constraints & package catalog
    └── ARCHITECTURE_AND_DESIGN.md                           # System Architecture, ERD, UML & Patterns
```

---

## 5. Day 3 Completed Milestones

1. **Domain Models & Enums:** Defined all 5 relational entities (`Customer`, `Account`, `TransactionRecord`, `TransactionAudit`, `FailedTransaction`) with business helpers (`debit`, `credit`, `isSufficientFor`, `isActive`).
2. **Immutable DTO Records:** Created `TransactionRequest` and `TransactionResult` using Java 21 `record`s for thread-safe data flow.
3. **Custom Exception Hierarchy:** Implemented specific business exceptions corresponding to each validation and execution failure category.
4. **Data Access Interfaces (DAO Pattern):** Created `AccountDAO`, `TransactionDAO`, and `CustomerDAO` contracts taking raw `Connection` to support service-level transaction boundaries.
5. **Connection Pooling Singleton:** Implemented `DatabaseConnectionManager` with HikariCP pool properties.
6. **DDL Schema Script:** Created `src/main/resources/schema.sql` with all constraints (`CHECK balance >= 0`, `CHECK amount > 0`, PK, FK, performance indexes).
7. **Verification:** Verified clean compilation and green test execution via Maven.
