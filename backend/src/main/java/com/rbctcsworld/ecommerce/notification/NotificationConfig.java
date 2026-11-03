package com.rbctcsworld.ecommerce.notification;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

/**
 * app.notifications.channel = log (default) | smtp | ses. A typo stops the start, and so does SES without a region,
 * keys or a valid sender address: a store that cannot send order e-mails must not open.
 */
@Configuration
@EnableScheduling
class NotificationConfig {

    /** What SES needs (app.notifications.ses.*); the keys are the standard AWS_* environment variables. */
    record Ses(String region, String accessKeyId, String secretAccessKey, String sessionToken, String configurationSet,
               String endpoint) {
    }

    @Bean
    MailChannel mailChannel(@Value("${app.notifications.channel:log}") String channel,
                            @Value("${app.notifications.smtp.host:localhost}") String host,
                            @Value("${app.notifications.smtp.port:1025}") int port,
                            @Value("${app.notifications.from:RbcTcsWorld <orders@rbctcsworld.test>}") String from,
                            @Value("${app.notifications.ses.region:}") String region,
                            @Value("${app.notifications.ses.access-key-id:}") String accessKeyId,
                            @Value("${app.notifications.ses.secret-access-key:}") String secretAccessKey,
                            @Value("${app.notifications.ses.session-token:}") String sessionToken,
                            @Value("${app.notifications.ses.configuration-set:}") String configurationSet,
                            @Value("${app.notifications.ses.endpoint:}") String endpoint,
                            @Value("${app.store.name:RbcTcsWorld Shop}") String storeName,
                            Clock clock) {
        MailChannel c = create(channel, host, port, from,
                new Ses(region, accessKeyId, secretAccessKey, sessionToken, configurationSet, endpoint), clock);
        if (c instanceof SmtpMailChannel smtp) smtp.storeName(storeName);      // the HTML header (ADR-0025)
        if (c instanceof SesMailChannel ses) ses.storeName(storeName);
        return c;
    }

    static MailChannel create(String channel, String host, int port, String from) {
        return create(channel, host, port, from, null, Clock.systemUTC());
    }

    static MailChannel create(String channel, String host, int port, String from, Ses ses, Clock clock) {
        return switch (channel.trim().toLowerCase()) {
            case "log" -> new LogMailChannel();
            case "smtp" -> new SmtpMailChannel(host, port, from);
            case "ses" -> ses(from, ses, clock);
            default -> throw new IllegalStateException("app.notifications.channel must be 'log', 'smtp' or 'ses', not '" + channel + "'");
        };
    }

    private static MailChannel ses(String from, Ses ses, Clock clock) {
        if (ses == null || blank(ses.region()) || !ses.region().matches("[a-z]{2}(-[a-z]+)+-\\d")) {
            throw new IllegalStateException("app.notifications.channel=ses needs SES_REGION, e.g. us-east-1");
        }
        if (blank(ses.accessKeyId()) || blank(ses.secretAccessKey())) {
            throw new IllegalStateException("app.notifications.channel=ses needs AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY");
        }
        try {
            new InternetAddress(from, true);
        } catch (AddressException e) {
            throw new IllegalStateException("MAIL_FROM is not a valid sender address: " + from);
        }
        String endpoint = blank(ses.endpoint()) ? "https://email." + ses.region() + ".amazonaws.com" : ses.endpoint().trim();
        return new SesMailChannel(endpoint, ses.region(), ses.accessKeyId().trim(), ses.secretAccessKey().trim(),
                blank(ses.sessionToken()) ? null : ses.sessionToken().trim(), from,
                blank(ses.configurationSet()) ? null : ses.configurationSet().trim(), Duration.ofSeconds(10), clock);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
