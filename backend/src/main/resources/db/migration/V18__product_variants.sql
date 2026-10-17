-- Product variants (size / colour), modelled like Amazon's parent and child items: every variant is a full
-- product row with its own SKU, price, stock (in warehouses), reviews and order history. Products that share a
-- variant_group are options of one item; size and colour tell them apart. Cart, orders, inventory, warehouses
-- and reports need no change at all: they already work per product.
ALTER TABLE products ADD COLUMN variant_group VARCHAR(40);
ALTER TABLE products ADD COLUMN size VARCHAR(20);
ALTER TABLE products ADD COLUMN color VARCHAR(30);
CREATE INDEX idx_products_variant_group ON products(variant_group);

-- Seed: a T-shirt in two colours and four sizes. Navy XL and Heather Grey M are sold out on purpose, so the
-- product page shows a size that cannot be picked.
INSERT INTO products (name, sku, category, price, stock, active, image_url, variant_group, size, color) VALUES
 ('Classic Cotton T-Shirt', 'TEE-CLS-NAV-S',  'clothing', 19.99, 12, TRUE, '/products/tee-navy.svg', 'TEE-CLASSIC', 'S',  'Navy'),
 ('Classic Cotton T-Shirt', 'TEE-CLS-NAV-M',  'clothing', 19.99, 20, TRUE, '/products/tee-navy.svg', 'TEE-CLASSIC', 'M',  'Navy'),
 ('Classic Cotton T-Shirt', 'TEE-CLS-NAV-L',  'clothing', 19.99, 15, TRUE, '/products/tee-navy.svg', 'TEE-CLASSIC', 'L',  'Navy'),
 ('Classic Cotton T-Shirt', 'TEE-CLS-NAV-XL', 'clothing', 21.99,  0, TRUE, '/products/tee-navy.svg', 'TEE-CLASSIC', 'XL', 'Navy'),
 ('Classic Cotton T-Shirt', 'TEE-CLS-GRY-S',  'clothing', 19.99,  8, TRUE, '/products/tee-grey.svg', 'TEE-CLASSIC', 'S',  'Heather Grey'),
 ('Classic Cotton T-Shirt', 'TEE-CLS-GRY-M',  'clothing', 19.99,  0, TRUE, '/products/tee-grey.svg', 'TEE-CLASSIC', 'M',  'Heather Grey'),
 ('Classic Cotton T-Shirt', 'TEE-CLS-GRY-L',  'clothing', 19.99, 10, TRUE, '/products/tee-grey.svg', 'TEE-CLASSIC', 'L',  'Heather Grey'),
 ('Classic Cotton T-Shirt', 'TEE-CLS-GRY-XL', 'clothing', 21.99,  6, TRUE, '/products/tee-grey.svg', 'TEE-CLASSIC', 'XL', 'Heather Grey');

-- Their stock goes into the warehouses like V17 did: half in Philadelphia, the rest in Pittsburgh.
INSERT INTO warehouse_stock (warehouse_id, product_id, quantity)
SELECT w.id, p.id, p.stock - p.stock / 2 FROM products p, warehouses w WHERE w.code = 'PIT' AND p.variant_group = 'TEE-CLASSIC';
INSERT INTO warehouse_stock (warehouse_id, product_id, quantity)
SELECT w.id, p.id, p.stock / 2 FROM products p, warehouses w WHERE w.code = 'PHL' AND p.variant_group = 'TEE-CLASSIC';
