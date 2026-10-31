-- Stripe Tax product tax code per product (ADR-0012), e.g. txcd_30011000 clothing and footwear: some states tax
-- clothing, food or books at another rate. NULL = the Stripe account's default code.
ALTER TABLE products ADD COLUMN tax_code VARCHAR(20);
