-- Flash deals (time-limited price, like Temu): a product may carry ONE deal. While now is inside
-- [deal_starts_at, deal_ends_at) (UTC) the price paid is deal_price; before and after it is price again, with no
-- job needed to switch it off. All three columns are set together or not at all.
ALTER TABLE products ADD COLUMN deal_price     NUMERIC(12,2);
ALTER TABLE products ADD COLUMN deal_starts_at TIMESTAMP;
ALTER TABLE products ADD COLUMN deal_ends_at   TIMESTAMP;
ALTER TABLE products ADD CONSTRAINT chk_products_deal CHECK (
    (deal_price IS NULL AND deal_starts_at IS NULL AND deal_ends_at IS NULL)
    OR (deal_price IS NOT NULL AND deal_starts_at IS NOT NULL AND deal_ends_at IS NOT NULL
        AND deal_price > 0 AND deal_price < price AND deal_ends_at > deal_starts_at));
CREATE INDEX idx_products_deal_ends ON products(deal_ends_at);
