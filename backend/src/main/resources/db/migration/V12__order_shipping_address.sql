-- Delivery address of an order (US addresses). A copy is stored on the order itself, not a link to an address
-- book: what was shipped must not change when the customer later edits an address.
-- Orders placed before this migration have no address (NULL); every new order must have one (OrderService).
ALTER TABLE orders ADD COLUMN ship_name  VARCHAR(80);
ALTER TABLE orders ADD COLUMN ship_line1 VARCHAR(100);
ALTER TABLE orders ADD COLUMN ship_line2 VARCHAR(100);
ALTER TABLE orders ADD COLUMN ship_city  VARCHAR(60);
ALTER TABLE orders ADD COLUMN ship_state VARCHAR(2);
ALTER TABLE orders ADD COLUMN ship_zip   VARCHAR(10);
ALTER TABLE orders ADD COLUMN ship_phone VARCHAR(20);
