package com.rbctcsworld.ecommerce.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marks ONE token as used. The "revokedAt is null" condition makes this the single winner when two
     * requests send the same token at the same time: only one update returns 1, the other sees 0 (= reuse).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

    /** Was any token of the family revoked after this moment? Then the successor was used (or logout/theft). */
    boolean existsByFamilyIdAndRevokedAtAfter(String familyId, LocalDateTime moment);

    /** Is the session still alive (a usable token in the family)? */
    boolean existsByFamilyIdAndRevokedAtIsNull(String familyId);

    /** Housekeeping at login: expired tokens of this user are useless, so they are removed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.userId = :userId and t.expiresAt < :now")
    int deleteExpired(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
