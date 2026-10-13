-- Which payment provider handled a transaction, and its own id (Stripe: pi_... for a charge, re_... for a
-- refund). The id is needed to refund through the provider and to find a payment in its dashboard.
-- Existing rows were all made by the mock gateway.
ALTER TABLE payment_transactions ADD COLUMN provider VARCHAR(10) NOT NULL DEFAULT 'mock';
ALTER TABLE payment_transactions ADD COLUMN provider_ref VARCHAR(64);
ALTER TABLE payment_transactions ADD CONSTRAINT ck_payment_tx_provider CHECK (provider IN ('mock', 'stripe'));
