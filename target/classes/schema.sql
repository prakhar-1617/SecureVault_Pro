-- ============================================================
-- SecureVault Pro — Database Schema
-- Run this once on a fresh MySQL instance:
--   mysql -u root -p < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS securevault
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE securevault;

-- ============================================================
-- Table: users
-- Stores registered users with security metadata.
-- failedAttempts / accountLocked implement the Account Locking
-- Mechanism discussed in interview Module 1.
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    user_id        INT          AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    salt           VARCHAR(64)  NOT NULL,
    hashed_password VARCHAR(128) NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    account_locked  TINYINT(1)  NOT NULL DEFAULT 0,
    last_login     DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB;

-- ============================================================
-- Table: files
-- Metadata for every file encrypted and stored by the vault.
-- The actual bytes live on disk under encrypted_files/.
-- ============================================================
CREATE TABLE IF NOT EXISTS files (
    file_id        INT          AUTO_INCREMENT PRIMARY KEY,
    owner_id       INT          NOT NULL,
    original_name  VARCHAR(255) NOT NULL,
    encrypted_path VARCHAR(512) NOT NULL,
    checksum       VARCHAR(64)  NOT NULL,   -- SHA-256 of original
    algorithm      VARCHAR(20)  NOT NULL,   -- AES / XOR / CAESAR
    pipeline       VARCHAR(512) NULL,       -- JSON pipeline steps
    file_size      BIGINT       NOT NULL,
    upload_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_owner     (owner_id),
    INDEX idx_algorithm (algorithm),
    INDEX idx_upload    (upload_time)
) ENGINE=InnoDB;

-- ============================================================
-- Table: credentials  (Password Vault)
-- Stores website passwords encrypted with the user's AES key.
-- ============================================================
CREATE TABLE IF NOT EXISTS credentials (
    cred_id           INT          AUTO_INCREMENT PRIMARY KEY,
    owner_id          INT          NOT NULL,
    website           VARCHAR(255) NOT NULL,
    username          VARCHAR(100) NOT NULL,
    encrypted_password VARBINARY(512) NOT NULL,
    notes             TEXT         NULL,
    last_modified     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_cred_owner   (owner_id),
    INDEX idx_cred_website (website)
) ENGINE=InnoDB;

-- ============================================================
-- Table: audit_logs
-- Append-only event log written by AuditLogger (Singleton).
-- Uses ConcurrentLinkedQueue + async flush.
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id     BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    INT          NULL,              -- NULL for system events
    action     VARCHAR(50)  NOT NULL,
    detail     TEXT         NULL,
    timestamp  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_user      (user_id),
    INDEX idx_log_timestamp (timestamp)
) ENGINE=InnoDB;

-- ============================================================
-- Table: analytics
-- Per-operation performance records consumed by AnalyticsService.
-- ============================================================
CREATE TABLE IF NOT EXISTS analytics (
    id             BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id        INT         NULL,
    algorithm      VARCHAR(20) NOT NULL,
    operation_type ENUM('ENCRYPT','DECRYPT') NOT NULL,
    duration_ms    BIGINT      NOT NULL,
    file_size      BIGINT      NOT NULL,
    thread_id      BIGINT      NOT NULL,
    queue_wait_ms  BIGINT      NOT NULL DEFAULT 0,
    memory_used_kb BIGINT      NOT NULL DEFAULT 0,
    success        TINYINT(1)  NOT NULL DEFAULT 1,
    timestamp      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_analytics_algo (algorithm),
    INDEX idx_analytics_time (timestamp)
) ENGINE=InnoDB;
