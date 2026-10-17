package com.rbctcsworld.ecommerce.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Due: waiting rows whose time has come, and SENDING rows whose lease ran out (sender crashed). */
    @Query("select n from Notification n where n.status in ('PENDING', 'SENDING') and n.nextAttemptAt <= :now order by n.id")
    List<Notification> findDue(@Param("now") LocalDateTime now, Pageable page);

    /**
     * Takes a row for this instance. The row must still look exactly as it was read (same next_attempt_at):
     * when two backends race for it, only one update returns 1.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.status = 'SENDING', n.nextAttemptAt = :leaseUntil "
            + "where n.id = :id and n.nextAttemptAt = :seen and n.status in ('PENDING', 'SENDING')")
    int claim(@Param("id") Long id, @Param("seen") LocalDateTime seen, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.status = 'SENT', n.sentAt = :now, n.attempts = n.attempts + 1, n.lastError = null "
            + "where n.id = :id")
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.status = :status, n.attempts = :attempts, n.nextAttemptAt = :next, "
            + "n.lastError = :error where n.id = :id")
    int markFailedAttempt(@Param("id") Long id, @Param("status") String status, @Param("attempts") int attempts,
                          @Param("next") LocalDateTime next, @Param("error") String error);

    /** Admin "send again" for a FAILED row; 0 when the row is not FAILED. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.status = 'PENDING', n.attempts = 0, n.nextAttemptAt = :now, n.lastError = null "
            + "where n.id = :id and n.status = 'FAILED'")
    int requeue(@Param("id") Long id, @Param("now") LocalDateTime now);

    List<Notification> findByOrderIdOrderByIdAsc(Long orderId);

    List<Notification> findTop100ByStatusOrderByIdDesc(String status);

    List<Notification> findTop100ByOrderByIdDesc();

    /** After a reset link was delivered, the database no longer needs it: the body is replaced. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.body = :text where n.id = :id")
    int redactBody(@Param("id") Long id, @Param("text") String text);
}
