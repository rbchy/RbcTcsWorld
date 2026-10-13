package com.rbctcsworld.ecommerce.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** app.notifications.channel = log (default) | smtp. A typo stops the start. */
@Configuration
@EnableScheduling
class NotificationConfig {

    @Bean
    MailChannel mailChannel(@Value("${app.notifications.channel:log}") String channel,
                            @Value("${app.notifications.smtp.host:localhost}") String host,
                            @Value("${app.notifications.smtp.port:1025}") int port,
                            @Value("${app.notifications.from:RbcTcsWorld <orders@rbctcsworld.test>}") String from) {
        return create(channel, host, port, from);
    }

    static MailChannel create(String channel, String host, int port, String from) {
        return switch (channel.trim().toLowerCase()) {
            case "log" -> new LogMailChannel();
            case "smtp" -> new SmtpMailChannel(host, port, from);
            default -> throw new IllegalStateException("app.notifications.channel must be 'log' or 'smtp', not '" + channel + "'");
        };
    }
}
