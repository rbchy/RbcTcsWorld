-- Customer messages from the contact form (ADR-0018). The owner answers each one from the admin page; the answer is
-- e-mailed through the outbox (notifications) like every other e-mail.
CREATE TABLE contact_messages (
    id            BIGSERIAL     PRIMARY KEY,
    ticket        VARCHAR(20)   NOT NULL UNIQUE,     -- MSG-XXXXXX, quoted in every e-mail about the message
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(254)  NOT NULL,            -- lower case; the account's own e-mail when logged in
    user_id       BIGINT,                             -- set when the sender was logged in
    topic         VARCHAR(20)   NOT NULL,            -- ORDER | RETURN | PRODUCT | PAYMENT | OTHER
    order_number  VARCHAR(40),
    message       VARCHAR(4000) NOT NULL,
    status        VARCHAR(10)   NOT NULL,            -- NEW | ANSWERED | CLOSED
    reply         VARCHAR(4000),
    answered_by   VARCHAR(255),
    answered_at   TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL
);
CREATE INDEX idx_contact_messages_email_created ON contact_messages (email, created_at);
CREATE INDEX idx_contact_messages_status_created ON contact_messages (status, created_at);
