-- Store admin can disable a customer account (fraud, abuse, a customer's request). A disabled account
-- cannot log in or refresh, and its access token stops working at once (JwtFilter checks every request).
-- created_at: when the account was opened (accounts from before this migration get the migration time).
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX idx_orders_created ON orders(created_at);
