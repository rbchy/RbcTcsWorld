package com.rbctcsworld.ecommerce.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock NotificationRepository notifications;
    @Mock MailChannel channel;
    @Mock EmailSuppressions suppressions;
    /** A clock the test can move forward. */
    static final class TestClock extends java.time.Clock {
        private java.time.Instant now = java.time.Instant.parse("2026-10-12T12:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private final TestClock clock = new TestClock();
    private NotificationDeliveryService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryService(notifications, channel, suppressions, clock, 20);
        now = LocalDateTime.now(clock);
    }

    private Notification row(long id, int attempts) {
        Notification n = new Notification(9L, "buyer@test.com", "ORDER_PLACED", "Order received", "Thanks", now.minusMinutes(1));
        ReflectionTestUtils.setField(n, "id", id);
        ReflectionTestUtils.setField(n, "attempts", attempts);
        return n;
    }

    private void due(Notification... rows) {
        when(notifications.findDue(now, PageRequest.of(0, 20))).thenReturn(List.of(rows));
    }

    @Test
    void claimedRowIsSentAndMarkedSent() {
        Notification n = row(1L, 0);
        due(n);
        when(notifications.claim(1L, n.getNextAttemptAt(), now.plus(NotificationDeliveryService.LEASE))).thenReturn(1);

        assertThat(service.sendDue()).isEqualTo(1);

        verify(channel).send("buyer@test.com", "Order received", "Thanks");
        verify(notifications).markSent(1L, now);
        verify(notifications, never()).redactBody(anyLong(), anyString());   // order e-mails keep their text
    }

    @Test
    void aDeliveredResetLinkIsRemovedFromTheDatabase() {
        Notification n = new Notification(null, "ada@test.com", "PASSWORD_RESET", "Reset", "link: abc", now.minusMinutes(1));
        ReflectionTestUtils.setField(n, "id", 4L);
        due(n);
        when(notifications.claim(4L, n.getNextAttemptAt(), now.plus(NotificationDeliveryService.LEASE))).thenReturn(1);

        service.sendDue();

        verify(channel).send("ada@test.com", "Reset", "link: abc");
        verify(notifications).redactBody(4L, "[password reset link removed after delivery]");
    }

    @Test
    void anUndeliveredResetLinkIsNotRedactedYet() {
        Notification n = new Notification(null, "ada@test.com", "PASSWORD_RESET", "Reset", "link: abc", now.minusMinutes(1));
        ReflectionTestUtils.setField(n, "id", 4L);
        due(n);
        when(notifications.claim(anyLong(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("smtp down")).when(channel).send(anyString(), anyString(), anyString());

        service.sendDue();

        verify(notifications, never()).redactBody(anyLong(), anyString());
    }

    @Test
    void aRowAnotherInstanceClaimedIsLeftAlone() {
        due(row(1L, 0));
        when(notifications.claim(anyLong(), any(), any())).thenReturn(0);

        assertThat(service.sendDue()).isZero();

        verify(channel, never()).send(anyString(), anyString(), anyString());
        verify(notifications, never()).markSent(anyLong(), any());
    }

    @Test
    void firstFailureRetriesAfterThirtySeconds() {
        due(row(1L, 0));
        when(notifications.claim(anyLong(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("SMTP: connection refused")).when(channel).send(anyString(), anyString(), anyString());

        assertThat(service.sendDue()).isZero();

        verify(notifications).markFailedAttempt(1L, Notification.PENDING, 1, now.plusSeconds(30),
                "java.lang.IllegalStateException: SMTP: connection refused");
        verify(notifications, never()).markSent(anyLong(), any());
    }

    @Test
    void fourthFailureWaitsFourMinutesFifthGivesUp() {
        Notification fourth = row(1L, 3);
        Notification fifth = row(2L, 4);
        due(fourth, fifth);
        when(notifications.claim(anyLong(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("down")).when(channel).send(anyString(), anyString(), anyString());

        service.sendDue();

        verify(notifications).markFailedAttempt(eq(1L), eq(Notification.PENDING), eq(4), eq(now.plusMinutes(4)), anyString());
        verify(notifications).markFailedAttempt(eq(2L), eq(Notification.FAILED), eq(5), eq(now), anyString());
    }

    @Test
    void oneBadRowDoesNotStopTheOthers() {
        Notification bad = row(1L, 0);
        Notification good = new Notification(9L, "other@test.com", "ORDER_PLACED", "S", "B", now.minusMinutes(1));
        ReflectionTestUtils.setField(good, "id", 2L);
        due(bad, good);
        when(notifications.claim(anyLong(), any(), any())).thenReturn(1);
        // lenient: the same method is called with another address, which strict stubbing would report as a problem
        org.mockito.Mockito.lenient().doThrow(new IllegalStateException("bad address"))
                .when(channel).send(eq("buyer@test.com"), anyString(), anyString());

        assertThat(service.sendDue()).isEqualTo(1);
        verify(notifications).markSent(2L, now);
    }

    @Test
    void sendTimeIsTakenAfterTheSend() {
        Notification n = row(1L, 0);
        due(n);
        when(notifications.claim(anyLong(), any(), any())).thenReturn(1);
        org.mockito.Mockito.doAnswer(inv -> { clock.advance(Duration.ofSeconds(3)); return null; })
                .when(channel).send(anyString(), anyString(), anyString());

        service.sendDue();

        verify(notifications).markSent(1L, now.plusSeconds(3));
    }

    @Test
    void nothingDueNothingSent() {
        due();
        assertThat(service.sendDue()).isZero();
        verify(notifications, never()).claim(anyLong(), any(), any());
    }

    @Test
    void schedulerRunsTheSameRound() {
        due();
        service.scheduled();
        verify(notifications).findDue(now, PageRequest.of(0, 20));
    }

    @Test
    void backoffDoubles() {
        assertThat(NotificationDeliveryService.backoff(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(NotificationDeliveryService.backoff(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(NotificationDeliveryService.backoff(3)).isEqualTo(Duration.ofMinutes(2));
        assertThat(NotificationDeliveryService.backoff(4)).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void longErrorsAreShortenedForTheColumn() {
        assertThat(NotificationDeliveryService.shorten("x".repeat(300))).hasSize(300).doesNotEndWith("...");
        assertThat(NotificationDeliveryService.shorten("x".repeat(301))).hasSize(300).endsWith("...");
    }

    @Test
    void channelChoice() {
        assertThat(NotificationConfig.create("log", "h", 1, "f").name()).isEqualTo("log");
        assertThat(NotificationConfig.create(" SMTP ", "localhost", 1025, "a@b.c").name()).isEqualTo("smtp");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> NotificationConfig.create("email", "h", 1, "f"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'email'");
        assertThat(new NotificationConfig().mailChannel("log", "h", 1, "f", "", "", "", "", "", "", java.time.Clock.systemUTC()))
                .isInstanceOf(LogMailChannel.class);
        assertThat(new NotificationConfig().mailChannel("ses", "h", 1, "a@b.c", "us-east-1", "AKID", "s", "", "", "",
                java.time.Clock.systemUTC())).isInstanceOf(SesMailChannel.class);
        new LogMailChannel().send("a@b.c", "subject", "body");      // only logs
    }

    // ---- the suppression list (SES bounces and complaints) ----

    @Test
    void anAddressThatBouncedIsNotSentToAndTheRowSaysWhy() {
        Notification n = row(5, 0);
        due(n);
        when(notifications.claim(eq(5L), any(), any())).thenReturn(1);
        when(suppressions.reason("buyer@test.com")).thenReturn(EmailSuppressions.BOUNCE);

        assertThat(service.sendDue()).isZero();

        verify(channel, never()).send(anyString(), anyString(), anyString());
        verify(notifications).markFailedAttempt(5L, Notification.FAILED, 0, now,
                "Not sent: the address is on the suppression list after a bounce");
        verify(notifications, never()).markSent(anyLong(), any());
    }

    @Test
    void anAddressThatComplainedIsNotSentToEither() {
        Notification n = row(6, 2);
        due(n);
        when(notifications.claim(eq(6L), any(), any())).thenReturn(1);
        when(suppressions.reason("buyer@test.com")).thenReturn(EmailSuppressions.COMPLAINT);
        service.sendDue();
        verify(notifications).markFailedAttempt(6L, Notification.FAILED, 2, now,
                "Not sent: the address is on the suppression list after a complaint");
        verify(channel, never()).send(anyString(), anyString(), anyString());
    }
}
