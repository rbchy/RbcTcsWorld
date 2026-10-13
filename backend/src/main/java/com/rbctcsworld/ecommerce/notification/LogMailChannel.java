package com.rbctcsworld.ecommerce.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development default: the e-mail goes to the log instead of a mail server. */
class LogMailChannel implements MailChannel {

    private static final Logger log = LoggerFactory.getLogger(LogMailChannel.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("E-mail to {}: {}", to, subject);
    }

    @Override
    public String name() {
        return "log";
    }
}
