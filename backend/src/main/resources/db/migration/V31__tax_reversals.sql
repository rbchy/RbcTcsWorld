-- Stripe Tax (ADR-0012): the recorded sale (tax_...) of a paid order, and the reversal sent when the order
-- is refunded (cancelled after payment, or a return approved). Both NULL with the flat rate.
ALTER TABLE orders ADD COLUMN tax_transaction_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN tax_reversal_id VARCHAR(64);
