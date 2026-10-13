package com.rbctcsworld.ecommerce.notification;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/** Plain SMTP with Jakarta Mail. Short timeouts: a hanging mail server only delays the outbox, never a request. */
class SmtpMailChannel implements MailChannel {

    private final Session session;
    private final String from;

    SmtpMailChannel(String host, int port, String from) {
        Properties p = new Properties();
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", String.valueOf(port));
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout", "5000");
        p.put("mail.smtp.writetimeout", "5000");
        this.session = Session.getInstance(p);
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            Transport.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("SMTP: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "smtp";
    }
}
