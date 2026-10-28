package com.rbctcsworld.ecommerce.legal;

import com.rbctcsworld.ecommerce.legal.LegalInfoController.LegalInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The store's legal details: trimmed, blanks as "not set yet", and never "reviewed" while anything is missing. */
class LegalInfoControllerTest {

    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 10, 28);

    private static LegalInfo info(String legalName, String street, String city, String state, String zip, String email,
                                  boolean reviewed) {
        return LegalInfoController.build(" RbcTcsWorld Shop ", legalName, street, city, state, zip, email, EFFECTIVE,
                reviewed, " Pennsylvania ", 30, new BigDecimal("5.99"), new BigDecimal("50.00"));
    }

    @Test
    void aRegisteredBusinessIsShownAsConfigured() {
        LegalInfo i = info(" RbcTcsWorld LLC ", " 1 Market St ", " Philadelphia ", " PA ", " 19107 ", " support@shop.example ", true);
        assertThat(i).isEqualTo(new LegalInfo("RbcTcsWorld Shop", "RbcTcsWorld LLC", "1 Market St", "Philadelphia", "PA",
                "19107", "support@shop.example", EFFECTIVE, true, "Pennsylvania", 30, new BigDecimal("5.99"),
                new BigDecimal("50.00"), true));
    }

    @Test
    void beforeTheBusinessIsRegisteredEverythingIsEmptyAndThePoliciesAreDrafts() {
        LegalInfo i = info("", " ", null, "", "", "", true);           // "reviewed" set by mistake: still a draft
        assertThat(i.legalName()).isNull();
        assertThat(i.street()).isNull();
        assertThat(i.city()).isNull();
        assertThat(i.contactEmail()).isNull();
        assertThat(i.complete()).isFalse();
        assertThat(i.reviewed()).isFalse();
        assertThat(i.returnWindowDays()).isEqualTo(30);
    }

    @ParameterizedTest(name = "missing {0}")
    @ValueSource(strings = {"legalName", "street", "city", "state", "zip", "email"})
    void anyMissingDetailKeepsThePoliciesDrafts(String missing) {
        LegalInfo i = info(missing.equals("legalName") ? "" : "RbcTcsWorld LLC", missing.equals("street") ? "" : "1 Market St",
                missing.equals("city") ? "" : "Philadelphia", missing.equals("state") ? "" : "PA",
                missing.equals("zip") ? "" : "19107", missing.equals("email") ? "" : "support@shop.example", true);
        assertThat(i.complete()).isFalse();
        assertThat(i.reviewed()).isFalse();
    }

    @Test
    void completeButNotYetReviewedIsADraft() {
        LegalInfo i = info("RbcTcsWorld LLC", "1 Market St", "Philadelphia", "PA", "19107", "support@shop.example", false);
        assertThat(i.complete()).isTrue();
        assertThat(i.reviewed()).isFalse();
    }

    @Test
    void theEndpointAnswersWithTheBuiltDetails() {
        LegalInfoController c = new LegalInfoController("Shop", "LLC", "St", "City", "PA", "19107", "a@b.c", " 2026-11-01 ", true,
                "Pennsylvania", 14, new BigDecimal("4.99"), new BigDecimal("35.00"));
        assertThat(c.legal().returnWindowDays()).isEqualTo(14);
        assertThat(c.legal().shippingFee()).isEqualByComparingTo("4.99");
        assertThat(c.legal().reviewed()).isTrue();
        assertThat(c.legal().effectiveDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    }
}
