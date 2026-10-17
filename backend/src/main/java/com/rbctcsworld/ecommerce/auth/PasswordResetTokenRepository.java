package com.rbctcsworld.ecommerce.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** How many links were sent to this customer since the given moment (rate limit). */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Uses ONE link up. "usedAt is null" makes it single-use even when two requests send it at the same time. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int useIfUnused(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** A new link (or a successful reset) makes every other open link of the customer worthless. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int useAllOf(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
