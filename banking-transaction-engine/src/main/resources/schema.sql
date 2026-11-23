-- ============================================================================
-- Banking Transaction Processing System - Normalized DDL Schema
-- Compatible with MySQL 8.0+ / MariaDB
-- ============================================================================

CREATE TABLE IF NOT EXISTS customer (
    customer_id         VARCHAR(36)     NOT NULL,
    full_name           VARCHAR(100)    NOT NULL,
    email               VARCHAR(100)    NOT NULL,
    phone_number        VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id),
    UNIQUE KEY uq_customer_email (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS account (
    account_id          VARCHAR(36)     NOT NULL,
    customer_id         VARCHAR(36)     NOT NULL,
    balance             DECIMAL(15, 2)  NOT NULL DEFAULT 0.00,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'INR',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_account_balance_non_negative CHECK (balance >= 0.00),
    CONSTRAINT chk_account_status_valid CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transaction (
    transaction_id          VARCHAR(64)     NOT NULL,
    source_account_id       VARCHAR(36)     NULL,
    destination_account_id  VARCHAR(36)     NULL,
    transaction_type        VARCHAR(20)     NOT NULL,
    amount                  DECIMAL(15, 2)  NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transaction_id),
    CONSTRAINT fk_txn_source FOREIGN KEY (source_account_id) REFERENCES account(account_id),
    CONSTRAINT fk_txn_dest FOREIGN KEY (destination_account_id) REFERENCES account(account_id),
    CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0.00),
    CONSTRAINT chk_transaction_type_valid CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_transaction_status_valid CHECK (status IN ('SUCCESS', 'FAILED', 'REJECTED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transaction_audit (
    audit_id                    BIGINT AUTO_INCREMENT NOT NULL,
    transaction_id              VARCHAR(64)     NOT NULL,
    source_balance_before       DECIMAL(15, 2)  NULL,
    source_balance_after        DECIMAL(15, 2)  NULL,
    destination_balance_before  DECIMAL(15, 2)  NULL,
    destination_balance_after   DECIMAL(15, 2)  NULL,
    execution_thread            VARCHAR(64)     NOT NULL,
    duration_ms                 BIGINT          NOT NULL,
    audited_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    CONSTRAINT fk_audit_transaction FOREIGN KEY (transaction_id) REFERENCES transaction(transaction_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS failed_transaction (
    failure_id                  BIGINT AUTO_INCREMENT NOT NULL,
    transaction_id              VARCHAR(64)     NOT NULL,
    error_category              VARCHAR(50)     NOT NULL,
    failure_reason              TEXT            NOT NULL,
    failed_at                   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (failure_id),
    CONSTRAINT fk_failure_transaction FOREIGN KEY (transaction_id) REFERENCES transaction(transaction_id)
) ENGINE=InnoDB;

-- Performance Indexes
CREATE INDEX idx_account_customer ON account(customer_id);
CREATE INDEX idx_txn_source_created ON transaction(source_account_id, created_at DESC);
CREATE INDEX idx_txn_dest_created ON transaction(destination_account_id, created_at DESC);
CREATE INDEX idx_failed_txn_category ON failed_transaction(error_category);
