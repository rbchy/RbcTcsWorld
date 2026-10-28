-- Addresses the shop must not e-mail again: a hard bounce (the mailbox does not exist) or a spam complaint,
-- reported by Amazon SES through SNS. Sending to them anyway harms the domain's reputation and can get SES suspended.
CREATE TABLE email_suppressions (
    email       VARCHAR(255) PRIMARY KEY,           -- lower case
    reason      VARCHAR(20)  NOT NULL,              -- BOUNCE | COMPLAINT
    detail      VARCHAR(300),
    created_at  TIMESTAMP    NOT NULL
);
