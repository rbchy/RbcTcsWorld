-- The owner's own words about a product (ADR-0021): shown on the product page, in its search-engine description and
-- in its schema.org data. Plain text; blank lines separate paragraphs. NULL = no description yet.
ALTER TABLE products ADD COLUMN description VARCHAR(4000);
