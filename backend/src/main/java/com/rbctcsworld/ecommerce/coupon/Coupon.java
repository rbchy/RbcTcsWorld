package com.rbctcsworld.ecommerce.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
public class Coupon {

    public static final String PERCENT = "PERCENT";
    public static final String FIXED = "FIXED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(nullable = false)
    private boolean active = true;

    protected Coupon() {
    }

    public Coupon(String code, String type, BigDecimal discountValue, BigDecimal minOrderAmount, Integer maxUses,
                  LocalDateTime validFrom, LocalDateTime validUntil) {
        this.code = code;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount == null ? BigDecimal.ZERO : minOrderAmount;
        this.maxUses = maxUses;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    /** Discount amount for a given subtotal (PERCENT of subtotal, or FIXED amount), never above the subtotal. */
    public BigDecimal discountFor(BigDecimal subtotal) {
        BigDecimal d = PERCENT.equals(type)
                ? subtotal.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : discountValue;
        return d.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean usageLimitReached() {
        return maxUses != null && usedCount >= maxUses;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getType() { return type; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public Integer getMaxUses() { return maxUses; }
    public int getUsedCount() { return usedCount; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    /** Changes the terms; the code and the usage count stay. */
    public void update(String type, BigDecimal discountValue, BigDecimal minOrderAmount, Integer maxUses,
                       LocalDateTime validFrom, LocalDateTime validUntil) {
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount == null ? BigDecimal.ZERO : minOrderAmount;
        this.maxUses = maxUses;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }
}
