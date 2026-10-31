package com.rbctcsworld.ecommerce.notification;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Addresses the shop no longer e-mails after a hard bounce or a spam complaint (ADR-0014), ADMIN only.
 * GET    /api/admin/email-suppressions?search=gmail   newest first, at most 200
 * DELETE /api/admin/email-suppressions?email=a@b.com  204 the address may be e-mailed again | 404 not on the list | 400 no address
 * The e-mail goes in the query, not the path: an address may contain characters a path does not allow.
 */
@RestController
@RequestMapping("/api/admin/email-suppressions")
public class SuppressionController {

    private static final Logger log = LoggerFactory.getLogger(SuppressionController.class);

    private final EmailSuppressions suppressions;

    public SuppressionController(EmailSuppressions suppressions) {
        this.suppressions = suppressions;
    }

    @GetMapping
    public List<EmailSuppressions.Suppression> list(@RequestParam(required = false) String search) {
        return suppressions.list(search);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lift(Authentication auth, @RequestParam(required = false) String email) {
        if (email == null || email.isBlank()) throw new BusinessRuleException("Say which e-mail address to allow again");
        EmailSuppressions.Suppression lifted = suppressions.lift(email);
        if (lifted == null) throw new NotFoundException("This address is not on the suppression list");
        log.info("{} allowed e-mail to {} again (was {}: {})", auth.getName(), lifted.email(), lifted.reason(), lifted.detail());
    }
}
