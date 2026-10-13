package com.rbctcsworld.ecommerce.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Every charge attempt (successful or not) and every refund. No full card number, no CVV - ever. */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    public static final String CHARGE = "CHARGE";
    public static final String REFUND = "REFUND";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** mock | stripe - internal, not part of the API. */
    @Column(nullable = false, length = 10)
    private String provider = "mock";

    /** The provider's id (Stripe pi_... / re_...) for refunds and support - internal, not part of the API. */
    @Column(name = "provider_ref", length = 64)
    private String providerReference;

    protected PaymentTransaction() {
    }

    public PaymentTransaction(Long orderId, String type, String status, BigDecimal amount, String cardLast4,
                              String failureReason) {
        this.orderId = orderId;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.cardLast4 = cardLast4;
        this.failureReason = failureReason;
    }

    public PaymentTransaction(Long orderId, String type, String status, BigDecimal amount, String cardLast4,
                              String failureReason, String provider, String providerReference) {
        this(orderId, type, status, amount, cardLast4, failureReason);
        this.provider = provider;
        this.providerReference = providerReference;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCardLast4() { return cardLast4; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    @JsonIgnore public String getProvider() { return provider; }
    @JsonIgnore public String getProviderReference() { return providerReference; }
}
