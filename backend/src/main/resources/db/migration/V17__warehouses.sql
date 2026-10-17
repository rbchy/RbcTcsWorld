-- Multi-warehouse inventory. products.stock stays the TOTAL on hand (catalog, cart and the atomic
-- overselling guard keep working unchanged); warehouse_stock says where those units are.
-- Invariant (checked by tests): for every product, products.stock = SUM(warehouse_stock.quantity).
CREATE TABLE warehouses (
    id        BIGSERIAL    PRIMARY KEY,
    code      VARCHAR(10)  NOT NULL UNIQUE,
    name      VARCHAR(80)  NOT NULL,
    city      VARCHAR(60)  NOT NULL,
    state     VARCHAR(2)   NOT NULL,
    priority  INTEGER      NOT NULL DEFAULT 100 CHECK (priority BETWEEN 1 AND 999),
    active    BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE warehouse_stock (
    warehouse_id  BIGINT   NOT NULL REFERENCES warehouses(id),
    product_id    BIGINT   NOT NULL REFERENCES products(id),
    quantity      INTEGER  NOT NULL CHECK (quantity >= 0),
    PRIMARY KEY (warehouse_id, product_id)
);
CREATE INDEX idx_warehouse_stock_product ON warehouse_stock(product_id);

-- Which warehouse ships which units of an order line; cancel and return put them back exactly there.
CREATE TABLE order_allocations (
    id            BIGSERIAL  PRIMARY KEY,
    order_id      BIGINT     NOT NULL REFERENCES orders(id),
    product_id    BIGINT     NOT NULL REFERENCES products(id),
    warehouse_id  BIGINT     NOT NULL REFERENCES warehouses(id),
    quantity      INTEGER    NOT NULL CHECK (quantity > 0)
);
CREATE INDEX idx_order_allocations_order ON order_allocations(order_id);

ALTER TABLE stock_movements ADD COLUMN warehouse_id BIGINT REFERENCES warehouses(id);

INSERT INTO warehouses (code, name, city, state, priority) VALUES
    ('PIT', 'Pittsburgh distribution center', 'Pittsburgh', 'PA', 10),
    ('PHL', 'Philadelphia warehouse', 'Philadelphia', 'PA', 20),
    ('NWK', 'Newark warehouse', 'Newark', 'NJ', 30);

-- Existing stock: half in Philadelphia, the rest (and every odd unit) in Pittsburgh.
INSERT INTO warehouse_stock (warehouse_id, product_id, quantity)
SELECT w.id, p.id, p.stock - p.stock / 2 FROM products p, warehouses w WHERE w.code = 'PIT';
INSERT INTO warehouse_stock (warehouse_id, product_id, quantity)
SELECT w.id, p.id, p.stock / 2 FROM products p, warehouses w WHERE w.code = 'PHL';
