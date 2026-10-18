-- Saved cards ("wallet"). Never the card number or the CVV: only what a receipt shows (brand, last 4, expiry)
-- plus the payment provider's own token for the card (Stripe: pm_...; mock: mock_pm_...), which is useless
-- outside the provider's account. One default per customer is kept by the service; at most 5 cards.
CREATE TABLE payment_methods (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    brand         VARCHAR(20)  NOT NULL,
    last4         VARCHAR(4)   NOT NULL,
    expiry_month  INT          NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year   INT          NOT NULL CHECK (expiry_year BETWEEN 2000 AND 2100),
    gateway       VARCHAR(20)  NOT NULL,
    token         VARCHAR(100) NOT NULL,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL
);
CREATE INDEX idx_payment_methods_user ON payment_methods(user_id);
