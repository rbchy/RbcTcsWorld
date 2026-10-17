package com.rbctcsworld.ecommerce.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** SQL for warehouses, the stock in each, and order allocations. Callers own the transaction. */
@Repository
public class WarehouseStore {

    static final RowMapper<Warehouse> WAREHOUSE = (rs, i) -> new Warehouse(rs.getLong("id"), rs.getString("code"),
            rs.getString("name"), rs.getString("city"), rs.getString("state"), rs.getInt("priority"), rs.getBoolean("active"));
    private static final String COLUMNS = "id, code, name, city, state, priority, active";

    private final JdbcTemplate jdbc;

    public WarehouseStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- warehouses

    public List<Warehouse> all() {
        return jdbc.query("select " + COLUMNS + " from warehouses order by priority, id", WAREHOUSE);
    }

    public Optional<Warehouse> find(long id) {
        return jdbc.query("select " + COLUMNS + " from warehouses where id = ?", WAREHOUSE, id).stream().findFirst();
    }

    public boolean codeExists(String code) {
        Integer n = jdbc.queryForObject("select count(*) from warehouses where code = ?", Integer.class, code);
        return n != null && n > 0;
    }

    /** Where new stock goes when nobody says where: the active warehouse with the lowest priority number. */
    public Warehouse defaultWarehouse() {
        return jdbc.query("select " + COLUMNS + " from warehouses where active = true order by priority, id limit 1",
                WAREHOUSE).stream().findFirst().orElseThrow(() -> new IllegalStateException("no active warehouse"));
    }

    public long insert(String code, String name, String city, String state, int priority) {
        jdbc.update("insert into warehouses (code, name, city, state, priority, active) values (?, ?, ?, ?, ?, true)",
                code, name, city, state, priority);
        return jdbc.queryForObject("select id from warehouses where code = ?", Long.class, code);
    }

    public void update(long id, String name, String city, String state, int priority, boolean active) {
        jdbc.update("update warehouses set name = ?, city = ?, state = ?, priority = ?, active = ? where id = ?",
                name, city, state, priority, active, id);
    }

    public int activeCount() {
        Integer n = jdbc.queryForObject("select count(*) from warehouses where active = true", Integer.class);
        return n == null ? 0 : n;
    }

    /** Units held in one warehouse (all products). */
    public long unitsIn(long warehouseId) {
        Long n = jdbc.queryForObject("select coalesce(sum(quantity), 0) from warehouse_stock where warehouse_id = ?",
                Long.class, warehouseId);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- stock per warehouse

    /**
     * What every warehouse holds of the product, rows LOCKED until the transaction ends (always called
     * after the product row is locked, so every transaction takes the locks in the same order).
     */
    public List<WarehouseAllocator.Stock> lockStock(long productId) {
        List<long[]> rows = jdbc.query("select warehouse_id, quantity from warehouse_stock where product_id = ? "
                + "order by warehouse_id for update", (rs, i) -> new long[]{rs.getLong(1), rs.getInt(2)}, productId);
        List<Warehouse> warehouses = all();
        return rows.stream().map(r -> new WarehouseAllocator.Stock(warehouses.stream().filter(w -> w.id() == r[0])
                .findFirst().orElseThrow(), (int) r[1])).toList();
    }

    public int quantity(long warehouseId, long productId) {
        List<Integer> q = jdbc.query("select quantity from warehouse_stock where warehouse_id = ? and product_id = ?",
                (rs, i) -> rs.getInt(1), warehouseId, productId);
        return q.isEmpty() ? 0 : q.get(0);
    }

    /** Takes units if the warehouse holds that many: true when taken (atomic, never below 0). */
    public boolean take(long warehouseId, long productId, int quantity) {
        return jdbc.update("update warehouse_stock set quantity = quantity - ? "
                + "where warehouse_id = ? and product_id = ? and quantity >= ?", quantity, warehouseId, productId, quantity) == 1;
    }

    /** Adds units (creates the row the first time this warehouse holds the product). */
    public void put(long warehouseId, long productId, int quantity) {
        int updated = jdbc.update("update warehouse_stock set quantity = quantity + ? where warehouse_id = ? and product_id = ?",
                quantity, warehouseId, productId);
        if (updated == 0) {
            jdbc.update("insert into warehouse_stock (warehouse_id, product_id, quantity) values (?, ?, ?)",
                    warehouseId, productId, quantity);
        }
    }

    // ---------------------------------------------------------------- order allocations

    public void allocate(long orderId, long productId, long warehouseId, int quantity) {
        jdbc.update("insert into order_allocations (order_id, product_id, warehouse_id, quantity) values (?, ?, ?, ?)",
                orderId, productId, warehouseId, quantity);
    }

    /** warehouse id -> quantity for one product of one order, in the order they were allocated. */
    public List<WarehouseAllocator.Allocation> allocations(long orderId, long productId) {
        return jdbc.query("select warehouse_id, quantity from order_allocations where order_id = ? and product_id = ? order by id",
                (rs, i) -> new WarehouseAllocator.Allocation(rs.getLong(1), rs.getInt(2)), orderId, productId);
    }

    /** Every allocation of an order, for the admin: which warehouse ships what. */
    public List<OrderAllocation> allocations(long orderId) {
        return jdbc.query("""
                select a.product_id, a.warehouse_id, w.code, w.city, w.state, a.quantity
                from order_allocations a join warehouses w on w.id = a.warehouse_id
                where a.order_id = ? order by a.id""",
                (rs, i) -> new OrderAllocation(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getInt(6)), orderId);
    }

    public record OrderAllocation(long productId, long warehouseId, String warehouseCode, String city, String state,
                                  int quantity) {
    }
}
