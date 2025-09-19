CREATE DATABASE IF NOT EXISTS posdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE posdb;

-- Auth
CREATE TABLE IF NOT EXISTS users (
    UserID       INT AUTO_INCREMENT PRIMARY KEY,
    Username     VARCHAR(64) NOT NULL UNIQUE,
    PasswordHash VARCHAR(255) NOT NULL,
    Role         VARCHAR(32) NOT NULL,
    Email        VARCHAR(128),
    Phone        VARCHAR(32),
    Status       VARCHAR(16) DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

-- POS master data (what your code reads)
CREATE TABLE IF NOT EXISTS items (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_code  VARCHAR(32) NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    restock_level INT NOT NULL DEFAULT 50
) ENGINE=InnoDB;

-- FEFO stock by batch
CREATE TABLE IF NOT EXISTS batches (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_code     VARCHAR(32) NOT NULL,
    expiry        DATE NULL,
    qty_on_shelf  INT NOT NULL DEFAULT 0,
    qty_in_store  INT NOT NULL DEFAULT 0,
    qty_in_main   INT NOT NULL DEFAULT 1000,
    INDEX idx_batches_item (item_code),
    INDEX idx_batches_expiry (expiry)
) ENGINE=InnoDB;

-- Bills + lines (your persistence + reporting)
CREATE TABLE IF NOT EXISTS bills (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bill_no      VARCHAR(64) NOT NULL,
    created_at   DATETIME    NOT NULL,
    subtotal     DECIMAL(10,2) NOT NULL,
    discount     DECIMAL(10,2) NOT NULL,
    tax          DECIMAL(10,2) NOT NULL,
    total        DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(16),
    paid_amount    DECIMAL(10,2),
    change_amount  DECIMAL(10,2),
    card_last4     VARCHAR(4),
    channel        VARCHAR(16),
    user_name      VARCHAR(64)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bill_lines (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bill_id    BIGINT NOT NULL,
    item_code  VARCHAR(32) NOT NULL,
    qty        INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    INDEX idx_bill_lines_bill (bill_id),
    CONSTRAINT fk_bill_lines_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Seed
INSERT IGNORE INTO users (Username, PasswordHash, Role, Email)
VALUES ('operator', 'nopass-dev', 'CASHIER', 'operator@example.com');

-- Seed an Inventory Manager user
INSERT IGNORE INTO users (Username, PasswordHash, Role, Email)
VALUES ('invmanager', '123456', 'INVENTORY_MANAGER', 'invmanager@example.com');

INSERT IGNORE INTO items (item_code, name, unit_price)
VALUES ('PA0001','Panadol 500mg', 35.00),
       ('CO0001','Coca-Cola 330ml', 12.00);

INSERT INTO batches (item_code, expiry, qty_on_shelf, qty_in_store, qty_in_main)
VALUES
    ('PA0001', DATE_ADD('2025-09-18', INTERVAL 180 DAY), 20, 80, 1000),
    ('CO0001', NULL, 30, 70, 1000);

-- Customers for ONLINE purchases
CREATE TABLE IF NOT EXISTS customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email        VARCHAR(128),
    status       VARCHAR(16) DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

-- Seed demo customers
INSERT IGNORE INTO customers (username, password_hash, email)
VALUES ('alice', 'nopass-demo', 'alice@example.com'),
       ('bob',   'nopass-demo', 'bob@example.com');

-- More seed items
INSERT IGNORE INTO items (item_code, name, unit_price) VALUES
('BR0001','Bread', 50.00),
('ML0001','Milk 1L', 80.00),
('EG0001','Eggs 6-pack', 120.00),
('OR0001','Orange Juice 1L', 150.00),
('AP0001','Apple 1kg', 200.00),
('BN0001','Banana 1kg', 100.00),
('CH0001','Chocolate Bar', 75.00),
('SN0001','Snacks Chips', 90.00),
('IC0001','Ice Cream 500ml', 300.00),
('WS0001','Water Bottle 1L', 60.00),
('TE0001','Tea Pack', 250.00),
('CF0001','Coffee 200g', 400.00),
('SH0001','Shampoo 200ml', 350.00),
('SO0001','Soap Bar', 45.00),
('DS0001','Detergent 1kg', 500.00),
('OL0001','Cooking Oil 1L', 650.00),
('SU0001','Sugar 1kg', 240.00),
('RC0001','Rice 5kg', 1200.00);


CREATE TABLE IF NOT EXISTS customers (
                                         id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         name          VARCHAR(200) NOT NULL,
    email         VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(32)
    ) ENGINE=InnoDB;

INSERT INTO batches (item_code, expiry, qty_on_shelf, qty_in_store) VALUES
('BR0001', DATE_ADD(CURDATE(), INTERVAL 180 DAY), 20, 80),
('ML0001', DATE_ADD(CURDATE(), INTERVAL 180 DAY), 20, 80),
('EG0001', DATE_ADD(CURDATE(), INTERVAL 180 DAY), 20, 80),
('OR0001', DATE_ADD(CURDATE(), INTERVAL 180 DAY), 20, 80),
('AP0001', DATE_ADD(CURDATE(), INTERVAL 180 DAY), 20, 80),
('BN0001', DATE_ADD(CURDATE(), INTERVAL 180 DAY), 20, 80),
('IC0001', DATE_ADD(CURDATE(), INTERVAL 180 DAY), 20, 80);

INSERT INTO batches (item_code, expiry, qty_on_shelf, qty_in_store) VALUES
('WS0001', '2026-12-23', 20, 80),
('TE0001', '2026-08-21', 20, 80),
('CF0001', '2026-11-17', 20, 80),
('SH0001', '2026-06-22', 20, 80),
('SO0001', '2026-07-21', 20, 80),
('DS0001', '2026-05-14', 20, 80),
('OL0001', '2026-09-28', 20, 80),
('SU0001', '2026-04-23', 20, 80),
('RC0001', '2026-03-27', 20, 80),
('CH0001', '2026-10-19', 20, 80),
('SN0001', '2026-08-14', 20, 80);


# -- Add MAIN store column on batches
ALTER TABLE batches
    ADD COLUMN qty_in_main INT NOT NULL DEFAULT 1000 AFTER qty_in_store;

-- Add per-item restock level (your code now reads this)
ALTER TABLE items
    ADD COLUMN restock_level INT NOT NULL DEFAULT 50 AFTER unit_price;

CREATE TABLE IF NOT EXISTS transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_code VARCHAR(16) NOT NULL,
    batch_id BIGINT,
    qty INT NOT NULL,
    transferred_by VARCHAR(64) NOT NULL,
    transferred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
