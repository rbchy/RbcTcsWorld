package com.rbctcsworld.ecommerce.contact;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.common.exception.TooManyRequestsException;
import com.rbctcsworld.ecommerce.notification.AccountEmailEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The contact form and the owner's answers (ADR-0018).
 * <ul>
 *   <li>Anybody may write, logged in or not. A logged-in customer writes with the account's own address, so nobody
 *       can write in someone else's name from an account.</li>
 *   <li>Spam protection without a CAPTCHA: a hidden honeypot field (filled in = silently dropped), at most
 *       {@value #PER_ADDRESS_PER_HOUR} messages per address per hour and {@value #ALL_PER_HOUR} for the whole shop.</li>
 *   <li>The "we received your message" e-mail never repeats what was written: otherwise anybody could make the shop
 *       send their text to a stranger's address.</li>
 *   <li>Every e-mail goes through the outbox, in the same transaction as the message: saved together or not at all.</li>
 * </ul>
 */
@Service
public class ContactService {

    static final int PER_ADDRESS_PER_HOUR = 3;
    static final int ALL_PER_HOUR = 200;
    static final int LIST_LIMIT = 200;
    static final String TOO_MANY = "You have sent several messages in the last hour. We will answer them; please wait before writing again";
    static final String BUSY = "Too many messages right now; please try again later";
    static final String EMAIL_NEEDED = "Please give your e-mail address so we can answer";
    private static final String TICKET_LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";   // no 0/O, 1/I/L

    /** What the form shows: how soon the shop answers. */
    public record ContactInfo(int replyDays, List<String> topics) {
    }

    /** What the sender gets back. {@code ticket} is quoted in every e-mail about the message. */
    public record Sent(String ticket, int replyDays) {
    }

    private final ContactMessageRepository messages;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String storeName;
    private final String ownerEmail;
    private final int replyDays;

    public ContactService(ContactMessageRepository messages, UserRepository users, ApplicationEventPublisher events, Clock clock,
                          @Value("${app.store.name:RbcTcsWorld Shop}") String storeName,
                          @Value("${app.store.contact-email:}") String contactEmail,
                          @Value("${app.seed.admin-email:admin@rbctcsworld.com}") String adminEmail,
                          @Value("${app.store.reply-days:2}") int replyDays) {
        this.messages = messages;
        this.users = users;
        this.events = events;
        this.clock = clock;
        this.storeName = storeName.trim();
        this.ownerEmail = contactEmail == null || contactEmail.isBlank() ? adminEmail.trim() : contactEmail.trim();
        this.replyDays = replyDays;
    }

    public ContactInfo info() {
        return new ContactInfo(replyDays, List.of("ORDER", "RETURN", "PRODUCT", "PAYMENT", "OTHER"));
    }

    /** {@code accountEmail}: the logged-in user's e-mail, or null for a visitor. */
    @Transactional
    public Sent send(ContactRequest r, String accountEmail) {
        if (r.website() != null && !r.website().isBlank()) {
            return new Sent(newTicket(), replyDays);          // a bot: looks accepted, nothing is saved or sent
        }
        Optional<AppUser> account = accountEmail == null ? Optional.empty() : users.findByEmail(accountEmail);
        String email = account.map(AppUser::getEmail).orElse(r.email());
        if (email == null || email.isBlank()) throw new BusinessRuleException(EMAIL_NEEDED);
        email = email.trim().toLowerCase(Locale.ROOT);
        String text = r.message().trim();
        if (text.length() < 10) throw new BusinessRuleException("The message must be at least 10 characters");

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime hourAgo = now.minusHours(1);
        if (messages.countByEmailAndCreatedAtAfter(email, hourAgo) >= PER_ADDRESS_PER_HOUR) {
            throw new TooManyRequestsException(TOO_MANY, 3600);
        }
        if (messages.countByCreatedAtAfter(hourAgo) >= ALL_PER_HOUR) throw new TooManyRequestsException(BUSY, 600);

        String order = r.orderNumber() == null || r.orderNumber().isBlank() ? null : r.orderNumber().trim().toUpperCase(Locale.ROOT);
        ContactMessage m = messages.save(new ContactMessage(newTicket(), r.name().trim(), email,
                account.map(AppUser::getId).orElse(null), r.topic(), order, text, now));

        events.publishEvent(new AccountEmailEvent(email, AccountEmailEvent.Type.CONTACT_RECEIVED,
                "We received your message " + m.getTicket(),
                "Hello " + m.getName() + ",\n\nthank you for writing to " + storeName + ". Your message " + m.getTicket()
                        + " has arrived and we will answer within " + replyDays + " business days.\n\n"
                        + "If you did not write to us, you can ignore this e-mail."));
        events.publishEvent(new AccountEmailEvent(ownerEmail, AccountEmailEvent.Type.CONTACT_NEW,
                "New message " + m.getTicket() + " (" + m.getTopic() + ") from " + m.getEmail(),
                "From: " + m.getName() + " <" + m.getEmail() + ">" + (m.getUserId() == null ? " (not logged in)" : " (customer account)")
                        + "\nTopic: " + m.getTopic() + (order == null ? "" : "\nOrder: " + order)
                        + "\n\n" + text + "\n\nAnswer it in Admin -> Messages."));
        return new Sent(m.getTicket(), replyDays);
    }

    @Transactional(readOnly = true)
    public List<ContactMessage> list(String status) {
        PageRequest top = PageRequest.of(0, LIST_LIMIT);
        if (status == null || status.isBlank()) return messages.findAllByOrderByCreatedAtDescIdDesc(top);
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of(ContactMessage.NEW, ContactMessage.ANSWERED, ContactMessage.CLOSED).contains(s)) {
            throw new BusinessRuleException("status must be NEW, ANSWERED or CLOSED");
        }
        return messages.findByStatusOrderByCreatedAtDescIdDesc(s, top);
    }

    /** The owner's answer: saved on the message and e-mailed to the sender with their own message quoted below. */
    @Transactional
    public ContactMessage reply(long id, String text, String admin) {
        ContactMessage m = get(id);
        String answer = text == null ? "" : text.trim();
        if (answer.length() < 2 || answer.length() > 4000) throw new BusinessRuleException("The answer must be 2 to 4000 characters");
        m.answer(answer, admin, LocalDateTime.now(clock));
        events.publishEvent(new AccountEmailEvent(m.getEmail(), AccountEmailEvent.Type.CONTACT_REPLY,
                "Re: your message " + m.getTicket(),
                "Hello " + m.getName() + ",\n\n" + answer + "\n\n" + storeName + "\n\n"
                        + "--- Your message " + m.getTicket() + " ---\n" + m.getMessage()));
        return m;
    }

    @Transactional
    public ContactMessage close(long id) {
        ContactMessage m = get(id);
        m.close();
        return m;
    }

    private ContactMessage get(long id) {
        return messages.findById(id).orElseThrow(() -> new NotFoundException("Message " + id + " not found"));
    }

    /** MSG- and 6 characters that cannot be mistaken for each other (about 900 million possibilities). */
    String newTicket() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder t = new StringBuilder("MSG-");
            for (int i = 0; i < 6; i++) t.append(TICKET_LETTERS.charAt(random.nextInt(TICKET_LETTERS.length())));
            if (!messages.existsByTicket(t.toString())) return t.toString();
        }
        throw new IllegalStateException("No free ticket number");
    }
}
