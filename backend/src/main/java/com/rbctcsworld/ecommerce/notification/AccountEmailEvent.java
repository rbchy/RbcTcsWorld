package com.rbctcsworld.ecommerce.notification;

/**
 * An account e-mail (not about an order): password reset link, "your password was changed", contact form e-mails.
 * Published inside the transaction that caused it; NotificationService writes it into the outbox there.
 */
public record AccountEmailEvent(String recipient, Type type, String subject, String body) {

    public enum Type {
        PASSWORD_RESET, PASSWORD_CHANGED, LOGIN_CODE, TWO_FACTOR_CHANGED,
        /** Contact form (ADR-0018): "we received your message" to the sender, the message to the owner, the answer. */
        CONTACT_RECEIVED, CONTACT_NEW, CONTACT_REPLY,
        /** The last e-mail to a customer who deleted the account (ADR-0022). */
        ACCOUNT_DELETED
    }
}
