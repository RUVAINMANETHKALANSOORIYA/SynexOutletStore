-- Migration to add missing columns for restock functionality

USE posdb;

-- Add qty_in_main column to batches table
ALTER TABLE batches
ADD COLUMN qty_in_main INT NOT NULL DEFAULT 1000 AFTER qty_in_store;

-- Add restock_level column to items table
ALTER TABLE items
ADD COLUMN restock_level INT NOT NULL DEFAULT 50 AFTER unit_price;

-- Update existing batches to have some main store quantity
UPDATE batches SET qty_in_main = 1000 WHERE qty_in_main = 0;

-- Insert sample items with expiry dates and proper quantities
INSERT IGNORE INTO items (item_code, name, unit_price, restock_level) VALUES
('BR0001','Bread', 50.00, 30),
('ML0001','Milk 1L', 80.00, 25),
('EG0001','Eggs 6-pack', 120.00, 20),
('OR0001','Orange Juice 1L', 150.00, 15),
('AP0001','Apple 1kg', 200.00, 40),
('BN0001','Banana 1kg', 100.00, 35),
('CH0001','Chocolate Bar', 75.00, 50),
('SN0001','Snacks Chips', 90.00, 30),
('IC0001','Ice Cream 500ml', 300.00, 10),
('WS0001','Water Bottle 1L', 60.00, 60),
('TE0001','Tea Pack', 250.00, 25),
('CF0001','Coffee 200g', 400.00, 20),
('SH0001','Shampoo 200ml', 350.00, 15),
('SO0001','Soap Bar', 45.00, 40),
('DS0001','Detergent 1kg', 500.00, 12),
('OL0001','Cooking Oil 1L', 650.00, 18),
('SU0001','Sugar 1kg', 240.00, 35),
('RC0001','Rice 5kg', 1200.00, 8);

-- Insert batches for the new items with various expiry dates
INSERT IGNORE INTO batches (item_code, expiry, qty_on_shelf, qty_in_store, qty_in_main) VALUES
('BR0001', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 15, 20, 800),
('ML0001', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 10, 15, 900),
('EG0001', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 8, 12, 700),
('OR0001', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 12, 18, 850),
('AP0001', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 25, 35, 950),
('BN0001', DATE_ADD(CURDATE(), INTERVAL 2 DAY), 20, 30, 600),
('CH0001', DATE_ADD(CURDATE(), INTERVAL 60 DAY), 40, 45, 1200),
('SN0001', DATE_ADD(CURDATE(), INTERVAL 30 DAY), 25, 35, 1000),
('IC0001', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 5, 8, 400),
('WS0001', NULL, 50, 70, 1500),
('TE0001', DATE_ADD(CURDATE(), INTERVAL 180 DAY), 20, 25, 800),
('CF0001', DATE_ADD(CURDATE(), INTERVAL 365 DAY), 15, 20, 700),
('SH0001', DATE_ADD(CURDATE(), INTERVAL 120 DAY), 10, 15, 600),
('SO0001', DATE_ADD(CURDATE(), INTERVAL 90 DAY), 30, 40, 900),
('DS0001', DATE_ADD(CURDATE(), INTERVAL 365 DAY), 8, 12, 500),
('OL0001', DATE_ADD(CURDATE(), INTERVAL 90 DAY), 12, 18, 650),
('SU0001', DATE_ADD(CURDATE(), INTERVAL 365 DAY), 25, 35, 800),
('RC0001', DATE_ADD(CURDATE(), INTERVAL 365 DAY), 5, 8, 300);

-- Set some items to low stock for testing restock report
UPDATE batches SET qty_on_shelf = 5, qty_in_store = 8, qty_in_main = 15 WHERE item_code = 'IC0001';
UPDATE batches SET qty_on_shelf = 3, qty_in_store = 5, qty_in_main = 20 WHERE item_code = 'RC0001';
UPDATE batches SET qty_on_shelf = 8, qty_in_store = 10, qty_in_main = 25 WHERE item_code = 'DS0001';
