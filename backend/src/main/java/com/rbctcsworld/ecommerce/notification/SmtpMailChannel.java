package com.rbctcsworld.ecommerce.notification;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.util.Properties;

/** Plain SMTP with Jakarta Mail. Short timeouts: a hanging mail server only delays the outbox, never a request. */
class SmtpMailChannel implements MailChannel {

    private final Session session;
    private final String from;
    /** The name in the header of the HTML part (ADR-0025); app.store.name in production. */
    private String storeName = "RbcTcsWorld Shop";

    /** Sends the text and its HTML twin as multipart/alternative (ADR-0025). */
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

    SmtpMailChannel storeName(String name) {
        if (name != null && !name.isBlank()) this.storeName = name.trim();
        return this;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");
            MimeMultipart alternative = new MimeMultipart("alternative");
            MimeBodyPart text = new MimeBodyPart();                     // first: the plainest part comes first
            text.setText(body, "UTF-8");
            MimeBodyPart html = new MimeBodyPart();
            html.setContent(EmailHtml.render(storeName, subject, body), "text/html; charset=UTF-8");
            alternative.addBodyPart(text);
            alternative.addBodyPart(html);
            message.setContent(alternative);
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
