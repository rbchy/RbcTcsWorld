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

    /** A guest checkout: no password of its own yet. Setting one with "Forgot password" makes it a full account. */
    @Column(nullable = false)
    private boolean guest;

    /** Opt-in: after the password, a 6-digit code sent by e-mail is needed to sign in. */
    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    /** Set by the database when the account is created. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    /** Set when the customer deleted the account (ADR-0022); the row only stays for the orders that point at it. */
    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

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
    public boolean isGuest() { return guest; }
    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean enabled) { this.twoFactorEnabled = enabled; }

    /** A guest account (see AuthService.guest). */
    public static AppUser guest(String email, String unusablePasswordHash) {
        AppUser u = new AppUser(email, unusablePasswordHash, Role.CUSTOMER);
        u.guest = true;
        return u;
    }

    /** The guest chose a password: from now on a normal customer account. */
    public void claim() {
        this.guest = false;
    }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }

    public void setActive(boolean active) { this.active = active; }

    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }

    /**
     * Account deleted by its owner: no e-mail (a placeholder that can never receive mail), no usable password, no
     * second factor, inactive. Signing in, password reset and new e-mails all stop; the address may register again.
     */
    public void erase(String unusablePasswordHash, java.time.LocalDateTime now) {
        this.email = "deleted-" + id + "@deleted.invalid";
        this.password = unusablePasswordHash;
        this.twoFactorEnabled = false;
        this.active = false;
        this.deletedAt = now;
    }
}
