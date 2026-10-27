-- Stripe Elements: the card is entered in Stripe's own form, so this database never sees it.
-- payment_intents: the one PaymentIntent of an order (the browser confirms it with Stripe). settled_at is set once,
-- by whichever comes first - the confirm call or the webhook - so an order is never paid twice.
CREATE TABLE payment_intents (
    order_id    BIGINT        PRIMARY KEY REFERENCES orders(id),
    intent_id   VARCHAR(64)   NOT NULL UNIQUE,
    amount      NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    settled_at  TIMESTAMP
);

-- Every Stripe webhook event handled, by Stripe's event id: Stripe sends an event again until it gets a 2xx,
-- and each one must change the shop only once.
CREATE TABLE stripe_events (
    event_id     VARCHAR(100) PRIMARY KEY,
    type         VARCHAR(60)  NOT NULL,
    received_at  TIMESTAMP    NOT NULL
);
