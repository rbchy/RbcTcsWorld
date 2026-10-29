package com.rbctcsworld.ecommerce.contact;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contact form (ADR-0018).
 * GET  /api/contact                         public  200 {replyDays, topics}
 * POST /api/contact                         public  201 {ticket, replyDays} | 400 | 429 (3 per address per hour)
 * GET  /api/admin/messages?status=NEW       ADMIN   200 newest first, at most 200 | 400 bad status
 * POST /api/admin/messages/{id}/reply       ADMIN   200 {text} e-mailed to the sender | 400 | 404 | 409 already answered/closed
 * POST /api/admin/messages/{id}/close       ADMIN   200 | 404 | 409 already closed
 */
@RestController
public class ContactController {

    /** A message as the owner sees it. */
    public record MessageView(long id, String ticket, String name, String email, boolean customer, String topic,
                              String orderNumber, String message, String status, String reply, String answeredBy,
                              LocalDateTime answeredAt, LocalDateTime createdAt) {
        static MessageView of(ContactMessage m) {
            return new MessageView(m.getId(), m.getTicket(), m.getName(), m.getEmail(), m.getUserId() != null, m.getTopic(),
                    m.getOrderNumber(), m.getMessage(), m.getStatus(), m.getReply(), m.getAnsweredBy(), m.getAnsweredAt(),
                    m.getCreatedAt());
        }
    }

    public record ReplyRequest(String text) {
    }

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @GetMapping("/api/contact")
    public ContactService.ContactInfo info() {
        return service.info();
    }

    @PostMapping("/api/contact")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactService.Sent send(Authentication auth, @Valid @RequestBody ContactRequest r) {
        String account = auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()) ? null : auth.getName();
        return service.send(r, account);
    }

    @GetMapping("/api/admin/messages")
    public List<MessageView> list(@RequestParam(required = false) String status) {
        return service.list(status).stream().map(MessageView::of).toList();
    }

    @PostMapping("/api/admin/messages/{id}/reply")
    public MessageView reply(Authentication auth, @PathVariable long id, @RequestBody ReplyRequest r) {
        return MessageView.of(service.reply(id, r.text(), auth.getName()));
    }

    @PostMapping("/api/admin/messages/{id}/close")
    public MessageView close(@PathVariable long id) {
        return MessageView.of(service.close(id));
    }
}
