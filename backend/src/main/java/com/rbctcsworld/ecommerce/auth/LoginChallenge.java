package com.rbctcsworld.ecommerce.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One pending two-factor sign-in (V21): hashes only, never the code itself. */
@Entity
@Table(name = "login_challenges")
public class LoginChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "challenge_hash", nullable = false, unique = true, length = 64)
    private String challengeHash;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected LoginChallenge() {
    }

    public LoginChallenge(Long userId, String challengeHash, String codeHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.userId = userId;
        this.challengeHash = challengeHash;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getChallengeHash() { return challengeHash; }
    public String getCodeHash() { return codeHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public LocalDateTime getUsedAt() { return usedAt; }
}
