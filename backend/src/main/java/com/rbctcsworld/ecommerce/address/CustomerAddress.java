package com.rbctcsworld.ecommerce.address;

import com.rbctcsworld.ecommerce.order.ShippingAddress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One saved delivery address of one customer. */
@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 30)
    private String label;

    @Column(name = "full_name", nullable = false, length = 80) private String fullName;
    @Column(nullable = false, length = 100) private String line1;
    @Column(length = 100) private String line2;
    @Column(nullable = false, length = 60) private String city;
    @Column(nullable = false, length = 2) private String state;
    @Column(nullable = false, length = 10) private String zip;
    @Column(length = 20) private String phone;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected CustomerAddress() {
    }

    public CustomerAddress(Long userId) {
        this.userId = userId;
    }

    /** Copies a validated, normalised address (ShippingAddress.normalized()). */
    public void change(String label, ShippingAddress a) {
        this.label = label;
        this.fullName = a.fullName();
        this.line1 = a.line1();
        this.line2 = a.line2();
        this.city = a.city();
        this.state = a.state();
        this.zip = a.zip();
        this.phone = a.phone();
    }

    public ShippingAddress address() {
        return new ShippingAddress(fullName, line1, line2, city, state, zip, phone);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getLabel() { return label; }
    public boolean isDefault() { return isDefault; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
