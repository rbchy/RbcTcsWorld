package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Named CustomerOrder because "Order" clashes with SQL ORDER BY and JPA's Order type. */
@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String status = OrderStatus.PLACED;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    /** Stripe Tax calculation (taxcalc_...) the tax came from; recorded as a sale once paid. Null with the flat rate. */
    @Column(name = "tax_calculation_id", length = 64)
    private String taxCalculationId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(length = 10)
    private String carrier;

    @Column(name = "tracking_number", unique = true, length = 30)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // delivery address: a copy taken when the order is placed (V12); null for orders placed before
    @Column(name = "ship_name", length = 80)
    private String shipName;

    @Column(name = "ship_line1", length = 100)
    private String shipLine1;

    @Column(name = "ship_line2", length = 100)
    private String shipLine2;

    @Column(name = "ship_city", length = 60)
    private String shipCity;

    @Column(name = "ship_state", length = 2)
    private String shipState;

    @Column(name = "ship_zip", length = 10)
    private String shipZip;

    @Column(name = "ship_phone", length = 20)
    private String shipPhone;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderEvent> events = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderNumber, Long userId) {
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.events.add(new OrderEvent(this, OrderStatus.PLACED, "Order placed"));
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        item.attachTo(this);
        items.add(item);
        totalItems += item.getQuantity();
        subtotal = subtotal.add(item.getLineTotal());
    }

    /** Stores the final price breakdown (subtotal itself is accumulated by addItem). */
    public void applyPricing(BigDecimal discount, BigDecimal shippingFee, BigDecimal tax, BigDecimal total,
                             String couponCode) {
        this.discount = discount;
        this.shippingFee = shippingFee;
        this.tax = tax;
        this.total = total;
        this.couponCode = couponCode;
    }

    /** Stores where the order goes (expects a normalized address). */
    public void shipTo(ShippingAddress a) {
        this.shipName = a.fullName();
        this.shipLine1 = a.line1();
        this.shipLine2 = a.line2();
        this.shipCity = a.city();
        this.shipState = a.state();
        this.shipZip = a.zip();
        this.shipPhone = a.phone();
    }

    /** The delivery address, or null for an order placed before addresses existed. */
    public ShippingAddress getShippingAddress() {
        return shipName == null ? null
                : new ShippingAddress(shipName, shipLine1, shipLine2, shipCity, shipState, shipZip, shipPhone);
    }

    // ------------------------------------------------------------ state machine

    /** The ONLY way the status changes: checks OrderStatus.ALLOWED (409 otherwise) and records a timeline event. */
    public void transitionTo(String target, String note) {
        if (!OrderStatus.canMove(status, target)) {
            throw new ConflictException("Order " + orderNumber + " cannot move from " + status + " to " + target);
        }
        status = target;
        events.add(new OrderEvent(this, target, note));
    }

    public boolean isPaid() {
        return OrderStatus.PAID.equals(status);
    }

    /** Throws 409 unless the order may currently move to {@code target} (used before side effects like charging). */
    public void assertCanMoveTo(String target) {
        if (!OrderStatus.canMove(status, target)) {
            throw new ConflictException("Order " + orderNumber + " cannot move from " + status + " to " + target);
        }
    }

    public void markPaid() {
        transitionTo(OrderStatus.PAID, "Payment received");
        paidAt = LocalDateTime.now();
    }

    public void ship(String carrier, String trackingNumber) {
        transitionTo(OrderStatus.SHIPPED, "Shipped with " + carrier + ", tracking " + trackingNumber);
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = LocalDateTime.now();
    }

    public void deliver() {
        transitionTo(OrderStatus.DELIVERED, "Delivered");
        this.deliveredAt = LocalDateTime.now();
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED, "Order cancelled");
        cancelledAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public int getTotalItems() { return totalItems; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getTotal() { return total; }
    public String getCouponCode() { return couponCode; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public String getCarrier() { return carrier; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocalDateTime getShippedAt() { return shippedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public List<OrderEvent> getEvents() { return events; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public List<OrderItem> getItems() { return items; }

    public String getTaxCalculationId() { return taxCalculationId; }

    public void recordTaxCalculation(String calculationId) {
        this.taxCalculationId = calculationId;
    }
}
