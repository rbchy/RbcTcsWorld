-- Product photos uploaded by the store owner (ADR-0017). Kept in the database, so the daily encrypted backup,
-- the off-site copy and the restore drill cover them with no extra step. Only the re-encoded picture is stored
-- (never the upload itself); its name is the SHA-256 of these bytes, so the same picture is stored once.
CREATE TABLE product_pictures (
    id            BIGSERIAL     PRIMARY KEY,
    sha256        VARCHAR(64)   NOT NULL UNIQUE,     -- lower-case hex; also the file name in the address
    content_type  VARCHAR(20)   NOT NULL,            -- image/jpeg | image/png
    width         INTEGER       NOT NULL,
    height        INTEGER       NOT NULL,
    size_bytes    INTEGER       NOT NULL,
    data          BYTEA         NOT NULL,
    uploaded_by   VARCHAR(255)  NOT NULL,
    uploaded_at   TIMESTAMP     NOT NULL
);
