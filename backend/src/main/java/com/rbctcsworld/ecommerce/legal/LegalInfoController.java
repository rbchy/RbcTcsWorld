package com.rbctcsworld.ecommerce.legal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * GET /api/store/legal - PUBLIC. Who runs the store and the numbers the policy pages quote (return window, shipping
 * price), read from the same settings the code uses, so a policy can never promise something else than the shop does.
 *
 * The business details (STORE_LEGAL_NAME, STORE_STREET ..., STORE_CONTACT_EMAIL) stay empty until the owner has
 * registered the business; the storefront then shows placeholders. "reviewed" (STORE_POLICIES_REVIEWED) is true only
 * after a lawyer has read the policies; until then every policy page says it is a draft.
 */
@RestController
public class LegalInfoController {

    public record LegalInfo(String storeName, String legalName, String street, String city, String state, String zip,
                            String contactEmail, LocalDate effectiveDate, boolean reviewed, String governingLaw,
                            int returnWindowDays, BigDecimal shippingFee, BigDecimal freeShippingThreshold,
                            boolean complete) {
    }

    private final LegalInfo info;

    public LegalInfoController(@Value("${app.store.name:RbcTcsWorld Shop}") String storeName,
                               @Value("${app.store.legal-name:}") String legalName,
                               @Value("${app.store.street:}") String street,
                               @Value("${app.store.city:}") String city,
                               @Value("${app.store.state:}") String state,
                               @Value("${app.store.zip:}") String zip,
                               @Value("${app.store.contact-email:}") String contactEmail,
                               @Value("${app.store.policies-effective:2026-10-28}") String effectiveDate,
                               @Value("${app.store.policies-reviewed:false}") boolean reviewed,
                               @Value("${app.store.governing-law:Pennsylvania}") String governingLaw,
                               @Value("${app.returns.window-days}") int returnWindowDays,
                               @Value("${app.pricing.shipping-fee}") BigDecimal shippingFee,
                               @Value("${app.pricing.free-shipping-threshold}") BigDecimal freeShippingThreshold) {
        this.info = build(storeName, legalName, street, city, state, zip, contactEmail, LocalDate.parse(effectiveDate.trim()), reviewed,
                governingLaw, returnWindowDays, shippingFee, freeShippingThreshold);
    }

    static LegalInfo build(String storeName, String legalName, String street, String city, String state, String zip,
                           String contactEmail, LocalDate effectiveDate, boolean reviewed, String governingLaw,
                           int returnWindowDays, BigDecimal shippingFee, BigDecimal freeShippingThreshold) {
        String n = blankToNull(legalName);
        String s = blankToNull(street);
        String c = blankToNull(city);
        String st = blankToNull(state);
        String z = blankToNull(zip);
        String e = blankToNull(contactEmail);
        boolean complete = n != null && s != null && c != null && st != null && z != null && e != null;
        return new LegalInfo(storeName.trim(), n, s, c, st, z, e, effectiveDate, reviewed && complete, governingLaw.trim(),
                returnWindowDays, shippingFee, freeShippingThreshold, complete);
    }

    @GetMapping("/api/store/legal")
    public LegalInfo legal() {
        return info;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
