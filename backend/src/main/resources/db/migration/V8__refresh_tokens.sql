-- Refresh tokens: the short-lived access token (JWT, 15 min) is renewed with a long-lived refresh token.
-- Only a SHA-256 hash of the token is stored, so a leaked database cannot be used to log in.
-- Rotation: every refresh revokes the used token and issues a new one in the same FAMILY. A revoked token
-- that comes back means it was stolen (or replayed), so the whole family is revoked (reuse detection).
CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    family_id   VARCHAR(36)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked_at  TIMESTAMP,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
