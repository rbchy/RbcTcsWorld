package com.rbctcsworld.ecommerce.contact;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One message from the contact form and, once the owner has answered, the answer (ADR-0018). */
@Entity
@Table(name = "contact_messages")
public class ContactMessage {

    public static final String NEW = "NEW";
    public static final String ANSWERED = "ANSWERED";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String ticket;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 20)
    private String topic;

    @Column(name = "order_number", length = 40)
    private String orderNumber;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(nullable = false, length = 10)
    private String status = NEW;

    @Column(length = 4000)
    private String reply;

    @Column(name = "answered_by")
    private String answeredBy;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ContactMessage() {
    }

    public ContactMessage(String ticket, String name, String email, Long userId, String topic, String orderNumber,
                          String message, LocalDateTime createdAt) {
        this.ticket = ticket;
        this.name = name;
        this.email = email;
        this.userId = userId;
        this.topic = topic;
        this.orderNumber = orderNumber;
        this.message = message;
        this.createdAt = createdAt;
    }

    /** One answer per message; a closed message cannot be answered any more. */
    public void answer(String reply, String by, LocalDateTime at) {
        if (!NEW.equals(status)) throw new ConflictException("This message was already " + status.toLowerCase());
        this.reply = reply;
        this.answeredBy = by;
        this.answeredAt = at;
        this.status = ANSWERED;
    }

    public void close() {
        if (CLOSED.equals(status)) throw new ConflictException("This message is already closed");
        this.status = CLOSED;
    }

    public Long getId() { return id; }
    public String getTicket() { return ticket; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Long getUserId() { return userId; }
    public String getTopic() { return topic; }
    public String getOrderNumber() { return orderNumber; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public String getReply() { return reply; }
    public String getAnsweredBy() { return answeredBy; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
