-- Test script to verify batch discounts are working
-- Run this in your database to create test data

-- First, let's create a test item if it doesn't exist
INSERT INTO items (item_code, item_name, unit_price, restock_level)
VALUES ('TEST001', 'Test Apple', 10.00, 50)
ON CONFLICT (item_code) DO NOTHING;

-- Create a test batch for this item with some inventory
INSERT INTO batches (item_code, expiry_date, qty_on_shelf, qty_in_store, qty_in_main)
VALUES ('TEST001', '2025-12-31', 0, 100, 200);

-- Get the batch ID that was just created
-- Note: Replace the batch_id below with the actual ID from your batches table
-- You can find it by running: SELECT id FROM batches WHERE item_code = 'TEST001' ORDER BY id DESC LIMIT 1;

-- Create a 20% discount for this batch (replace 'BATCH_ID_HERE' with actual batch ID)
INSERT INTO batch_discounts (batch_id, discount_type, discount_value, reason, valid_from, valid_until, created_by, created_at, is_active)
VALUES (
    (SELECT id FROM batches WHERE item_code = 'TEST001' ORDER BY id DESC LIMIT 1),
    'PERCENTAGE',
    20.00,
    'Test discount for debugging',
    NOW() - INTERVAL '1 hour',
    NOW() + INTERVAL '30 days',
    'admin',
    NOW(),
    TRUE
);

-- Verify the discount was created
SELECT
    bd.id,
    bd.batch_id,
    b.item_code,
    bd.discount_type,
    bd.discount_value,
    bd.reason,
    bd.is_active,
    bd.valid_from,
    bd.valid_until
FROM batch_discounts bd
JOIN batches b ON bd.batch_id = b.id
WHERE b.item_code = 'TEST001';
