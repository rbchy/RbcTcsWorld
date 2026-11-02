-- Which version of the terms and policies an order was placed under (ADR-0024): their effective date, and whether they
-- were lawyer-reviewed or still drafts then. NULL for orders placed before this was recorded.
ALTER TABLE orders ADD COLUMN policies_effective DATE;
ALTER TABLE orders ADD COLUMN policies_reviewed BOOLEAN;
