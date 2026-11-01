-- Self-service account deletion (ADR-0022): the user row stays (orders, payments and refunds must be kept for tax and
-- accounting law and point at it), but its e-mail and password are replaced and deleted_at is set.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
