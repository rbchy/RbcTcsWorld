-- "Forgot password": one row per reset link e-mailed to a customer. Only the SHA-256 hash of the token is
-- stored (like refresh tokens), so a copy of the database cannot be used to take over an account.
-- A link works once (used_at), for 30 minutes (expires_at), and a newer link makes older ones unusable.
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    created_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id, created_at);
