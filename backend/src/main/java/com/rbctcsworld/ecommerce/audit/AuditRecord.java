package com.rbctcsworld.ecommerce.audit;

import java.time.LocalDateTime;

/** One stored audit row (table audit_log). */
public record AuditRecord(long seq, LocalDateTime occurredAt, String actor, String entityType, long entityId,
                          String action, String field, String oldValue, String newValue, String prevHash,
                          String hash) {
}
