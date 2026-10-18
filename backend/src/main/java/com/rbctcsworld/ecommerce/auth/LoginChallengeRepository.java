package com.rbctcsworld.ecommerce.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, Long> {

    Optional<LoginChallenge> findByChallengeHash(String challengeHash);

    /** Codes sent to this customer since the given moment (rate limit). */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Uses ONE challenge up; "usedAt is null" makes it single-use even for two simultaneous requests. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LoginChallenge c set c.usedAt = :now where c.id = :id and c.usedAt is null")
    int useIfUnused(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Counts a wrong guess in the database, so parallel guesses cannot beat the limit. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LoginChallenge c set c.attempts = c.attempts + 1 where c.id = :id")
    int countAttempt(@Param("id") Long id);

    /** A newer code makes older open ones worthless. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LoginChallenge c set c.usedAt = :now where c.userId = :userId and c.usedAt is null")
    int useAllOf(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
