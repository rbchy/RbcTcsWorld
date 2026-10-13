package com.rbctcsworld.ecommerce.notification;

/** How an e-mail leaves the shop: SMTP (Mailpit in tests, a real provider in production) or the log (development). */
public interface MailChannel {

    /** Throws a RuntimeException when the message was not accepted; the delivery service retries later. */
    void send(String to, String subject, String body);

    String name();
}
