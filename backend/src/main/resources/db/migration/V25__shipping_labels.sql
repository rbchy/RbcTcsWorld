-- Shipping labels bought through the shipping provider (Shippo). All null with simulated shipping.
ALTER TABLE orders ADD COLUMN label_url VARCHAR(500);
ALTER TABLE orders ADD COLUMN label_cost NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN label_ref VARCHAR(64);
