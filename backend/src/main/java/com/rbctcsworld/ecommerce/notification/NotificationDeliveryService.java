package com.rbctcsworld.ecommerce.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Sends due outbox rows. Every few seconds (app.notifications.poll-ms) on every backend instance:
 *
 *  1. read due rows (oldest first, a batch at a time)
 *  2. claim each one with a conditional UPDATE - two instances never send the same row
 *  3. send; success -> SENT, failure -> try again after 30 s, 1, 2, 4 minutes; the 5th failure -> FAILED
 *
 * At-least-once: an instance that dies after the mail server accepted the message but before "SENT" was
 * written leaves a SENDING row whose lease expires, and the e-mail is sent again. Losing an order
 * confirmation is worse than a rare duplicate.
 */
@Service
public class NotificationDeliveryService {

    static final String REDACTED_CODE = "[sign-in code removed after delivery]";
    static final String REDACTED = "[password reset link removed after delivery]";

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    static final int MAX_ATTEMPTS = 5;
    static final Duration LEASE = Duration.ofMinutes(2);

    private final NotificationRepository notifications;
    private final MailChannel channel;
    private final Clock clock;
    private final int batchSize;

    public NotificationDeliveryService(NotificationRepository notifications, MailChannel channel, Clock clock,
                                       @Value("${app.notifications.batch-size:20}") int batchSize) {
        this.notifications = notifications;
        this.channel = channel;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-ms:2000}", initialDelayString = "${app.notifications.poll-ms:2000}")
    public void scheduled() {
        sendDue();
    }

    /** Returns how many e-mails were sent in this round. */
    public int sendDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        int sent = 0;
        for (Notification n : notifications.findDue(now, PageRequest.of(0, batchSize))) {
            if (notifications.claim(n.getId(), n.getNextAttemptAt(), now.plus(LEASE)) == 0) {
                continue;                                            // another instance took it
            }
            try {
                channel.send(n.getRecipient(), n.getSubject(), n.getBody());
                notifications.markSent(n.getId(), LocalDateTime.now(clock));
                if (AccountEmailEvent.Type.PASSWORD_RESET.name().equals(n.getType())) {
                    // the link would let anyone with database access take over the account until it expires
                    notifications.redactBody(n.getId(), REDACTED);
                } else if (AccountEmailEvent.Type.LOGIN_CODE.name().equals(n.getType())) {
                    notifications.redactBody(n.getId(), REDACTED_CODE);
                }
                sent++;
            } catch (RuntimeException e) {
                failed(n, e);
            }
        }
        return sent;
    }

    private void failed(Notification n, RuntimeException e) {
        int attempts = n.getAttempts() + 1;
        String error = shorten(e.toString());
        if (attempts >= MAX_ATTEMPTS) {
            notifications.markFailedAttempt(n.getId(), Notification.FAILED, attempts, LocalDateTime.now(clock), error);
            log.error("Notification {} ({}) gave up after {} attempts: {}", n.getId(), n.getType(), attempts, error);
        } else {
            notifications.markFailedAttempt(n.getId(), Notification.PENDING, attempts,
                    LocalDateTime.now(clock).plus(backoff(attempts)), error);
            log.warn("Notification {} ({}) attempt {} failed, retry in {}: {}", n.getId(), n.getType(), attempts,
                    backoff(attempts), error);
        }
    }

    /** 30 s after the 1st failure, then 1, 2, 4 minutes. */
    static Duration backoff(int attempts) {
        return Duration.ofSeconds(30L << (attempts - 1));
    }

    static String shorten(String text) {
        return text.length() <= 300 ? text : text.substring(0, 297) + "...";
    }
}
