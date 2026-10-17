-- Audit trail: who changed what, when - products (price, stock, name, ...) and the status of orders.
-- Append-only: rows are only ever inserted (the database refuses UPDATE/DELETE, see db/vendor/*/V15).
-- Tamper-evident: every row carries the SHA-256 of the row before it (hash chain), so a row that was
-- changed, removed or put in another order breaks the chain (GET /api/admin/audit/verify).
-- One row per changed field, so "who changed the price of product 5" is one simple query.
CREATE TABLE audit_log (
    seq          BIGINT        PRIMARY KEY,
    occurred_at  TIMESTAMP     NOT NULL,
    actor        VARCHAR(255)  NOT NULL,
    entity_type  VARCHAR(20)   NOT NULL,
    entity_id    BIGINT        NOT NULL,
    action       VARCHAR(30)   NOT NULL,
    field        VARCHAR(30)   NOT NULL,
    old_value    VARCHAR(500),
    new_value    VARCHAR(500),
    prev_hash    VARCHAR(64)   NOT NULL,
    hash         VARCHAR(64)   NOT NULL UNIQUE
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_log(actor);

-- The end of the chain. Its row is locked while new audit rows are written, so two transactions can
-- never append after the same row (the chain stays one line, in commit order).
CREATE TABLE audit_head (
    id         INTEGER      PRIMARY KEY CHECK (id = 1),
    last_seq   BIGINT       NOT NULL,
    last_hash  VARCHAR(64)  NOT NULL
);
INSERT INTO audit_head (id, last_seq, last_hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');
