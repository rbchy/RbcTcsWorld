-- Guest checkout: a guest is a customer account without a password of its own (a random one nobody knows).
-- It can shop, pay and follow its orders in this browser; "Forgot password" turns it into a full account.
ALTER TABLE users ADD COLUMN guest BOOLEAN NOT NULL DEFAULT FALSE;

-- Address book: up to 10 saved delivery addresses per customer, one of them the default.
-- Orders keep their own copy of the address (orders.ship_*), so editing or deleting a saved address never
-- changes an order that was already placed.
CREATE TABLE customer_addresses (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    label       VARCHAR(30),
    full_name   VARCHAR(80)   NOT NULL,
    line1       VARCHAR(100)  NOT NULL,
    line2       VARCHAR(100),
    city        VARCHAR(60)   NOT NULL,
    state       VARCHAR(2)    NOT NULL,
    zip         VARCHAR(10)   NOT NULL,
    phone       VARCHAR(20),
    is_default  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_customer_addresses_user ON customer_addresses(user_id);
