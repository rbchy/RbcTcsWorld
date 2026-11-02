package com.rbctcsworld.ecommerce.legal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * The version of the terms and policies in force right now (ADR-0024): their effective date and whether a lawyer has
 * reviewed them (false while any business detail is missing, see LegalInfoController). Every order records it, so
 * a later change of the policies never changes what an earlier customer agreed to.
 */
@Component
public class PolicyVersion {

    public record Version(LocalDate effective, boolean reviewed) {
    }

    private final Version current;

    /** The same settings the policy pages show. */
    @Autowired
    public PolicyVersion(LegalInfoController legal) {
        this(legal.legal().effectiveDate(), legal.legal().reviewed());
    }

    public PolicyVersion(LocalDate effective, boolean reviewed) {
        this.current = new Version(effective, reviewed);
    }

    public Version current() {
        return current;
    }
}
