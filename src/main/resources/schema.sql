-- ==========================================================
-- PayFlow Database Schema (PostgreSQL DDL)
-- Production schema with Foreign Keys, Unique Constraints, and Indexes
-- ==========================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(30),
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- 2. Wallets Table (One wallet per user, with pessimistic lock support and balance check)
CREATE TABLE IF NOT EXISTS wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wallet_balance CHECK (balance >= 0)
);
CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);

-- 3. Billers Table (Catalog of utility, telecom, dth providers)
CREATE TABLE IF NOT EXISTS billers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    account_number_regex VARCHAR(255),
    account_number_label VARCHAR(100) NOT NULL DEFAULT 'Consumer Number',
    commission_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0000,
    escrow_account_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_billers_category ON billers(category);
CREATE INDEX IF NOT EXISTS idx_billers_code ON billers(code);

-- 4. Biller Accounts (User's saved accounts/consumer numbers)
CREATE TABLE IF NOT EXISTS biller_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    biller_id BIGINT NOT NULL REFERENCES billers(id),
    consumer_number VARCHAR(100) NOT NULL,
    nickname VARCHAR(100),
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_biller_consumer UNIQUE (user_id, biller_id, consumer_number)
);
CREATE INDEX IF NOT EXISTS idx_biller_acc_user ON biller_accounts(user_id);

-- 5. Payment Transactions Table
CREATE TABLE IF NOT EXISTS payment_transactions (
    id VARCHAR(36) PRIMARY KEY,
    transaction_ref VARCHAR(64) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    wallet_id BIGINT NOT NULL REFERENCES wallets(id),
    biller_id BIGINT NOT NULL REFERENCES billers(id),
    consumer_number VARCHAR(100) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    fee NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(500),
    gateway_reference VARCHAR(100),
    gateway_response_code VARCHAR(50),
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP,
    settled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_txn_amount CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_txn_user_id ON payment_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_txn_status ON payment_transactions(status);
CREATE INDEX IF NOT EXISTS idx_txn_idempotency_key ON payment_transactions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_txn_created_at ON payment_transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_txn_retry ON payment_transactions(status, next_retry_at);

-- 6. Ledger Entries Table (Double-entry accounting: DEBIT / CREDIT)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL REFERENCES payment_transactions(id) ON DELETE CASCADE,
    account_type VARCHAR(50) NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance_after NUMERIC(18, 4),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ledger_amount CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_ledger_txn_id ON ledger_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_acc ON ledger_entries(account_type, account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_created ON ledger_entries(created_at);

-- 7. Scheduled Bills Table
CREATE TABLE IF NOT EXISTS scheduled_bills (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    biller_id BIGINT NOT NULL REFERENCES billers(id),
    consumer_number VARCHAR(100) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    frequency VARCHAR(50) NOT NULL,
    due_date DATE,
    next_execution_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_execution_date TIMESTAMP,
    last_transaction_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sched_amount CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_sched_user ON scheduled_bills(user_id);
CREATE INDEX IF NOT EXISTS idx_sched_status_date ON scheduled_bills(status, next_execution_date);

-- 8. Idempotency Records Table (Persistent audit and fallback for Redis)
CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL,
    http_status INT,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_idemp_user ON idempotency_records(user_id);
CREATE INDEX IF NOT EXISTS idx_idemp_expires ON idempotency_records(expires_at);

-- 9. Reconciliation Records Table (Audit of discrepancies against Gateway)
CREATE TABLE IF NOT EXISTS reconciliation_records (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(36) NOT NULL,
    ledger_status VARCHAR(50) NOT NULL,
    gateway_status VARCHAR(50) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    discrepancy_type VARCHAR(50) NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolution_notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_recon_batch ON reconciliation_records(batch_id);
CREATE INDEX IF NOT EXISTS idx_recon_txn ON reconciliation_records(transaction_id);
