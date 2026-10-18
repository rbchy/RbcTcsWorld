-- Two-factor sign-in by e-mail code (opt-in per account).
-- A login challenge is one row: the e-mailed 6-digit code and the random challenge id the browser keeps are stored
-- only as a SHA-256 hash of both together, so a copy of the database cannot be used to guess a code.
-- A challenge works once, for 10 minutes and for 5 wrong guesses.
ALTER TABLE users ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE login_challenges (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    challenge_hash  VARCHAR(64)  NOT NULL UNIQUE,
    code_hash       VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    used_at         TIMESTAMP
);
CREATE INDEX idx_login_challenges_user ON login_challenges(user_id, created_at);
