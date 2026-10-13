package com.rbctcsworld.ecommerce.notification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ADMIN only (SecurityConfig: /api/admin/**).
 * GET  /api/admin/notifications?status=FAILED | ?orderId=12  -> the outbox (latest 100)
 * POST /api/admin/notifications/{id}/retry                   -> 204 | 409 unless FAILED
 */
@RestController
@RequestMapping("/api/admin/notifications")
public class NotificationController {

    /** What the admin page shows; the e-mail body stays in the database. */
    public record NotificationView(Long id, Long orderId, String recipient, String type, String subject, String status,
                                   int attempts, String lastError, LocalDateTime createdAt, LocalDateTime sentAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getOrderId(), n.getRecipient(), n.getType(), n.getSubject(),
                    n.getStatus(), n.getAttempts(), n.getLastError(), n.getCreatedAt(), n.getSentAt());
        }
    }

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    List<NotificationView> list(@RequestParam(required = false) String status, @RequestParam(required = false) Long orderId) {
        return service.list(status, orderId).stream().map(NotificationView::of).toList();
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retry(@PathVariable Long id) {
        service.retry(id);
    }
}
