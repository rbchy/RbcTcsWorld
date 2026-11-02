-- Prepaid return labels (ADR-0023): bought through the shipping provider for an approved return, from the customer's
-- delivery address back to the store. All NULL until the admin buys one.
ALTER TABLE return_requests ADD COLUMN return_carrier VARCHAR(10);
ALTER TABLE return_requests ADD COLUMN return_tracking_number VARCHAR(40);
ALTER TABLE return_requests ADD COLUMN return_label_url VARCHAR(500);
ALTER TABLE return_requests ADD COLUMN return_label_cost NUMERIC(12, 2);
ALTER TABLE return_requests ADD COLUMN return_label_ref VARCHAR(64);
