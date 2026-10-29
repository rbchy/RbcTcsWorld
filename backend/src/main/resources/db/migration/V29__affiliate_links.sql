-- Affiliate products (ADR-0020): sold by a partner shop such as Amazon. The product page links there instead of
-- offering the cart; NULL = an ordinary product the shop sells itself.
ALTER TABLE products ADD COLUMN affiliate_url VARCHAR(500);
