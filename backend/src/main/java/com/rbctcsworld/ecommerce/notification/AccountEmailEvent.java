package com.rbctcsworld.ecommerce.notification;

/**
 * An account e-mail (not about an order): password reset link, "your password was changed".
 * Published inside the transaction that caused it; NotificationService writes it into the outbox there.
 */
public record AccountEmailEvent(String recipient, Type type, String subject, String body) {

    public enum Type {
        PASSWORD_RESET, PASSWORD_CHANGED, LOGIN_CODE, TWO_FACTOR_CHANGED
    }
}
