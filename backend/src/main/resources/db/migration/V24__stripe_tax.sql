-- Stripe Tax: the calculation an order's tax came from, recorded with Stripe as a sale once the order is paid.
ALTER TABLE orders ADD COLUMN tax_calculation_id VARCHAR(64);
