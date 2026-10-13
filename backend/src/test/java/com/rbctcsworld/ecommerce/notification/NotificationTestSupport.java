package com.rbctcsworld.ecommerce.notification;

/** Lets integration tests (other package) build the package-private SMTP channel like the config does. */
public final class NotificationTestSupport {

    private NotificationTestSupport() {
    }

    public static MailChannel smtp(String host, int port) {
        return NotificationConfig.create("smtp", host, port, "RbcTcsWorld <orders@rbctcsworld.test>");
    }
}
