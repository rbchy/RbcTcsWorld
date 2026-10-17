package com.rbctcsworld.ecommerce.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChangeTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyTheFieldsThatChangedAreListedInOrder() {
        Map<String, Object> before = AuditChange.fields("name", "Lamp", "price", new BigDecimal("10.00"), "stock", 5);
        Map<String, Object> after = AuditChange.fields("name", "Lamp", "price", new BigDecimal("12.50"), "stock", 9);
        assertThat(AuditChange.diff("PRODUCT", 5, "PRODUCT_UPDATED", before, after)).containsExactly(
                new AuditChange("PRODUCT", 5, "PRODUCT_UPDATED", "price", "10.00", "12.50"),
                new AuditChange("PRODUCT", 5, "PRODUCT_UPDATED", "stock", "5", "9"));
    }

    @Test
    void theSamePriceWrittenDifferentlyIsNoChange() {
        assertThat(AuditChange.diff("PRODUCT", 1, "U", AuditChange.fields("price", new BigDecimal("12.5")),
                AuditChange.fields("price", new BigDecimal("12.50")))).isEmpty();
        assertThat(AuditChange.same(new BigDecimal("1"), new BigDecimal("1.0"))).isTrue();
        assertThat(AuditChange.same(new BigDecimal("1"), new BigDecimal("1.01"))).isFalse();
        assertThat(AuditChange.same(new BigDecimal("1"), "1")).isFalse();
        assertThat(AuditChange.same(null, null)).isTrue();
        assertThat(AuditChange.same(null, "x")).isFalse();
    }

    @Test
    void aCreatedRecordListsEveryFieldThatHasAValue() {
        Map<String, Object> after = AuditChange.fields("name", "Lamp", "imageUrl", null, "active", true);
        assertThat(AuditChange.diff("PRODUCT", 7, "PRODUCT_CREATED", null, after)).containsExactly(
                new AuditChange("PRODUCT", 7, "PRODUCT_CREATED", "name", null, "Lamp"),
                new AuditChange("PRODUCT", 7, "PRODUCT_CREATED", "active", null, "true"));
    }

    @Test
    void aClearedValueIsStoredAsNull() {
        assertThat(AuditChange.diff("PRODUCT", 7, "U", AuditChange.fields("imageUrl", "/a.svg"),
                AuditChange.fields("imageUrl", null)))
                .containsExactly(new AuditChange("PRODUCT", 7, "U", "imageUrl", "/a.svg", null));
    }

    @Test
    void valuesAreStoredAsPlainTextOfAtMostFiveHundredCharacters() {
        assertThat(AuditChange.text(null)).isNull();
        assertThat(AuditChange.text(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(AuditChange.text(42)).isEqualTo("42");
        assertThat(AuditChange.text("x".repeat(500))).hasSize(500);
        assertThat(AuditChange.text("y".repeat(501))).isEqualTo("y".repeat(500));
    }

    @Test
    void fieldsKeepsTheGivenOrder() {
        assertThat(new ArrayList<>(AuditChange.fields("b", 1, "a", 2, "c", 3).keySet()))
                .containsExactly("b", "a", "c");
        assertThat(AuditChange.fields()).isEmpty();
    }

    @Test
    void theActorIsTheLoggedInUserOrSystem() {
        assertThat(AuditEvent.of(List.of()).actor()).isEqualTo("system");

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(AuditEvent.of(List.of()).actor()).isEqualTo("system");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@shop.test", null, List.of()));
        assertThat(AuditEvent.orderStatus(9, "PLACED", "PAID")).isEqualTo(new AuditEvent("admin@shop.test",
                List.of(new AuditChange("ORDER", 9, "ORDER_STATUS", "status", "PLACED", "PAID"))));
    }

    @Test
    void theEventKeepsItsOwnCopyOfTheChanges() {
        List<AuditChange> changes = new ArrayList<>(List.of(new AuditChange("ORDER", 1, "A", "status", "x", "y")));
        AuditEvent e = AuditEvent.of(changes);
        changes.clear();
        assertThat(e.changes()).hasSize(1);
    }
}
