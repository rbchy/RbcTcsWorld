package com.rbctcsworld.ecommerce.audit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Store admin only (/api/admin/**). Read-only: there is no endpoint that changes or deletes audit rows.
 * GET /api/admin/audit?entityType=PRODUCT&entityId=5&actor=a@b.com&limit=200  newest first | 400 bad limit
 * GET /api/admin/audit/verify                                               {valid, entries, brokenAtSeq, reason}
 * GET /api/admin/audit/export?...                                           text/csv download
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditRecord> find(@RequestParam(required = false) String entityType,
                                  @RequestParam(required = false) Long entityId,
                                  @RequestParam(required = false) String actor,
                                  @RequestParam(required = false) Integer limit) {
        return audit.find(entityType, entityId, actor, limit);
    }

    @GetMapping("/verify")
    public AuditChain.Verification verify() {
        return audit.verify();
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) Long entityId,
                                         @RequestParam(required = false) String actor) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-trail.csv\"")
                .body(audit.csv(entityType, entityId, actor));
    }
}
