package com.rbctcsworld.ecommerce.payment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.YearMonth;

/** A saved card (V22): brand, last 4, expiry and the provider's token. Never the number or the CVV. */
@Entity
@Table(name = "payment_methods")
public class SavedPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20) private String brand;
    @Column(nullable = false, length = 4) private String last4;
    @Column(name = "expiry_month", nullable = false) private int expiryMonth;
    @Column(name = "expiry_year", nullable = false) private int expiryYear;
    @Column(nullable = false, length = 20) private String gateway;
    @JsonIgnore @Column(nullable = false, length = 100) private String token;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected SavedPaymentMethod() {
    }

    public SavedPaymentMethod(Long userId, String brand, String last4, int expiryMonth, int expiryYear, String gateway, String token) {
        this.userId = userId;
        this.brand = brand;
        this.last4 = last4;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.gateway = gateway;
        this.token = token;
    }

    public boolean expiredAt(YearMonth now) {
        return YearMonth.of(expiryYear, expiryMonth).isBefore(now);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getBrand() { return brand; }
    public String getLast4() { return last4; }
    public int getExpiryMonth() { return expiryMonth; }
    public int getExpiryYear() { return expiryYear; }
    public String getGateway() { return gateway; }
    public String getToken() { return token; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean value) { this.isDefault = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
