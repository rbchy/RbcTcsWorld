package com.rbctcsworld.ecommerce.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One row per stock change. Sum of change_qty per product explains its current stock. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String RETURN_RESTOCK = "RETURN_RESTOCK";
    public static final String RECEIVED = "RECEIVED";
    public static final String COUNT_CORRECTION = "COUNT_CORRECTION";
    public static final String DAMAGED = "DAMAGED";
    public static final String TRANSFER_OUT = "TRANSFER_OUT";
    public static final String TRANSFER_IN = "TRANSFER_IN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "change_qty", nullable = false)
    private int changeQty;

    @Column(nullable = false, length = 30)
    private String reason;

    /** The warehouse the units left or entered (null for movements from before warehouses existed). */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StockMovement() {
    }

    public StockMovement(Long productId, Long orderId, int changeQty, String reason, int stockAfter) {
        this(productId, orderId, null, changeQty, reason, stockAfter);
    }

    /** stockAfter = the product's TOTAL stock (all warehouses) after this movement. */
    public StockMovement(Long productId, Long orderId, Long warehouseId, int changeQty, String reason, int stockAfter) {
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.orderId = orderId;
        this.changeQty = changeQty;
        this.reason = reason;
        this.stockAfter = stockAfter;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getOrderId() { return orderId; }
    public int getChangeQty() { return changeQty; }
    public String getReason() { return reason; }
    public int getStockAfter() { return stockAfter; }
    public Long getWarehouseId() { return warehouseId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
