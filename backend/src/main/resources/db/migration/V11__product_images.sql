-- Product pictures. A path to the storefront's own images (/products/<sku>.svg) or an https:// link an
-- admin enters. NULL = the storefront shows the picture of the product's category.
ALTER TABLE products ADD COLUMN image_url VARCHAR(500);

UPDATE products SET image_url = '/products/' || LOWER(sku) || '.svg'
 WHERE sku IN ('ELEC-MOUSE-001', 'ELEC-KEYB-002', 'ELEC-MON-003', 'ELEC-HUB-004',
               'HOME-BOTL-001', 'HOME-MUG-002', 'BOOK-QA-001', 'ELEC-HEAD-005');
