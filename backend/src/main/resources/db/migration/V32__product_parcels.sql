-- What a product weighs and measures in its shipping box (ADR-0013): Shippo rates the label for the real parcel instead
-- of the configured box and 16 oz per item. Ounces and inches with one decimal; NULL = not given (the defaults apply).
ALTER TABLE products ADD COLUMN weight_oz NUMERIC(6, 1);
ALTER TABLE products ADD COLUMN length_in NUMERIC(4, 1);
ALTER TABLE products ADD COLUMN width_in NUMERIC(4, 1);
ALTER TABLE products ADD COLUMN height_in NUMERIC(4, 1);
