package com.rbctcsworld.ecommerce.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = Role.CUSTOMER;

    /** false = disabled by the store admin: no login, no refresh, the access token stops working. */
    @Column(nullable = false)
    private boolean active = true;

    /** Set by the database when the account is created. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    protected AppUser() {
    }

    public AppUser(String email, String password) {
        this(email, password, Role.CUSTOMER);
    }

    public AppUser(String email, String password, String role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }

    /** Stores a new password hash (password reset). */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }

    public void setActive(boolean active) { this.active = active; }
}
