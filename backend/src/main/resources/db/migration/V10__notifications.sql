-- Transactional outbox for customer e-mails. A row is written in the SAME transaction as the order change
-- (so a rolled-back change never mails anyone); a background job sends due rows and retries failures.
--   PENDING -> (claimed) SENDING -> SENT
--                                 -> PENDING again with a later next_attempt_at (retry, growing waits)
--                                 -> FAILED after 5 attempts (admin can re-queue)
-- A SENDING row whose lease ran out (instance crashed mid-send) becomes due again: at-least-once delivery.
CREATE TABLE notifications (
    id               BIGSERIAL     PRIMARY KEY,
    order_id         BIGINT        REFERENCES orders(id),
    recipient        VARCHAR(255)  NOT NULL,
    type             VARCHAR(30)   NOT NULL,
    subject          VARCHAR(200)  NOT NULL,
    body             VARCHAR(4000) NOT NULL,
    status           VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    attempts         INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMP     NOT NULL,
    last_error       VARCHAR(300),
    created_at       TIMESTAMP     NOT NULL,
    sent_at          TIMESTAMP
);
CREATE INDEX idx_notifications_due ON notifications(status, next_attempt_at);
CREATE INDEX idx_notifications_order ON notifications(order_id);
