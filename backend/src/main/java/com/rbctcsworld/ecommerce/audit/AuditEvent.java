package com.rbctcsworld.ecommerce.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Published by a service inside its transaction; AuditService writes it just before that transaction
 * commits - so a change that is rolled back leaves no audit row, and a committed change always has one.
 * The actor is taken when the event is created: the logged-in user, or "system" (scheduled jobs, startup).
 */
public record AuditEvent(String actor, List<AuditChange> changes) {

    public static final String SYSTEM = "system";

    public static AuditEvent of(List<AuditChange> changes) {
        return new AuditEvent(currentActor(), List.copyOf(changes));
    }

    /** An order moved from one status to another. */
    public static AuditEvent orderStatus(long orderId, String from, String to) {
        return of(List.of(new AuditChange(AuditChange.ORDER, orderId, "ORDER_STATUS", "status", from, to)));
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName()) ? SYSTEM : auth.getName();
    }
}
