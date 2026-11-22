# Banking Transaction Processing System — Project Documentation

**Current Stage:** Architecture, Modeling & Design Phase  
**Last Updated:** Day 1  

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
| [**ARCHITECTURE_AND_DESIGN.md**](file:///c:/Users/chand/OneDrive/Desktop/banking-transaction-engine/documentation/ARCHITECTURE_AND_DESIGN.md) | **Complete System Architecture, ERD, UML & Design Patterns**<br>• System Reference Architecture (5-tier decoupled pipeline)<br>• Entity-Relationship Diagram (ERD) with 5 normalized tables & constraints<br>• UML Class Diagram (Pure Java records, DAOs, Services, Strategies)<br>• UML Sequence Diagrams (Mandatory concurrency race test & transfer deadlock avoidance)<br>• Design Patterns Applied (Singleton, DAO, Template Method, Strategy, Factory) | **Active & Completed** |

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

## 4. Project Directory Structure

```
banking-transaction-engine/
├── banking-transaction-engine/          # Maven project root
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/enterprise/banking/
│   │   │   │   └── BankingTransactionEngineApplication.java  # Core Java entry point
│   │   │   └── resources/
│   │   │       ├── database.properties                       # HikariCP JDBC connection settings
│   │   │       └── logback.xml                               # SLF4J / Logback configuration
│   │   └── test/
│   │       └── java/com/enterprise/banking/
│   │           └── BankingTransactionEngineApplicationTests.java # JUnit 5 sanity test
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml                                               # Pure Java Maven dependencies
└── documentation/
    ├── README.md                                             # Project setup, constraints & index
    └── ARCHITECTURE_AND_DESIGN.md                           # System Architecture, ERD, UML & Patterns
```
