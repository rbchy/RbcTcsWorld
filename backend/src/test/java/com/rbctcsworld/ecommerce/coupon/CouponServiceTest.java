package com.rbctcsworld.ecommerce.coupon;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    /** Fixed "now" = 2026-06-15 12:00 so validity windows are deterministic. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock CouponRepository coupons;
    @Mock CouponRedemptionRepository redemptions;
    @Mock ApplicationEventPublisher events;
    private CouponService service;

    @BeforeEach
    void setUp() {
        service = new CouponService(coupons, redemptions, CLOCK, events);
    }

    /** What the database does on save: the new coupon gets an id. */
    private static Coupon withId(org.mockito.invocation.InvocationOnMock i) {
        Coupon c = i.getArgument(0);
        ReflectionTestUtils.setField(c, "id", 21L);
        return c;
    }

    private Coupon coupon(String code, String type, String value, String min, Integer maxUses,
                          LocalDateTime from, LocalDateTime until) {
        Coupon c = new Coupon(code, type, new BigDecimal(value), new BigDecimal(min), maxUses, from, until);
        ReflectionTestUtils.setField(c, "id", 1L);
        return c;
    }

    @Test
    void codeIsCaseInsensitiveAndTrimmed() {
        Coupon c = coupon("SAVE5", Coupon.FIXED, "5.00", "0", null, null, null);
        when(coupons.findByCode("SAVE5")).thenReturn(Optional.of(c));

        assertThat(service.validate("  save5 ", 9L, new BigDecimal("10.00"))).isSameAs(c);
    }

    @Test
    void unknownOrInactiveCodeIsInvalid() {
        when(coupons.findByCode("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.validate("nope", 9L, BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Invalid coupon code");

        Coupon off = coupon("OFF", Coupon.FIXED, "5.00", "0", null, null, null);
        off.setActive(false);
        when(coupons.findByCode("OFF")).thenReturn(Optional.of(off));
        assertThatThrownBy(() -> service.validate("OFF", 9L, BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Invalid coupon code");
    }

    @Test
    void validityWindowBoundaries() {
        Coupon expired = coupon("OLD", Coupon.PERCENT, "10", "0", null, null, NOW.minusSeconds(1));
        Coupon future = coupon("NEW", Coupon.PERCENT, "10", "0", null, NOW.plusSeconds(1), null);
        Coupon lastSecond = coupon("EDGE", Coupon.PERCENT, "10", "0", null, null, NOW);
        when(coupons.findByCode("OLD")).thenReturn(Optional.of(expired));
        when(coupons.findByCode("NEW")).thenReturn(Optional.of(future));
        when(coupons.findByCode("EDGE")).thenReturn(Optional.of(lastSecond));

        assertThatThrownBy(() -> service.validate("OLD", 9L, BigDecimal.TEN)).hasMessageContaining("expired");
        assertThatThrownBy(() -> service.validate("NEW", 9L, BigDecimal.TEN)).hasMessageContaining("not active yet");
        assertThat(service.validate("EDGE", 9L, BigDecimal.TEN)).isSameAs(lastSecond); // valid until == now is OK
    }

    @Test
    void minimumOrderAmountBoundary() {
        Coupon c = coupon("SAVE5", Coupon.FIXED, "5.00", "25.00", null, null, null);
        when(coupons.findByCode("SAVE5")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.validate("SAVE5", 9L, new BigDecimal("24.99")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Minimum order amount");
        assertThat(service.validate("SAVE5", 9L, new BigDecimal("25.00"))).isSameAs(c);
    }

    @Test
    void secondUseBySameCustomerIsConflict() {
        Coupon c = coupon("WELCOME10", Coupon.PERCENT, "10", "0", null, null, null);
        when(coupons.findByCode("WELCOME10")).thenReturn(Optional.of(c));
        when(redemptions.existsByCouponIdAndUserId(1L, 9L)).thenReturn(true);

        assertThatThrownBy(() -> service.validate("WELCOME10", 9L, BigDecimal.TEN))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already used");
    }

    @Test
    void redeemFailsWhenAtomicCounterSaysLimitReached() {
        Coupon c = coupon("LIMITED", Coupon.FIXED, "1.00", "0", 1, null, null);
        when(coupons.incrementUsage(1L)).thenReturn(0);

        assertThatThrownBy(() -> service.redeem(c, 9L, 100L))
                .isInstanceOf(ConflictException.class).hasMessage("Coupon usage limit reached: LIMITED");
        verify(redemptions, never()).save(any());
    }

    @Test
    void discountMaths() {
        assertThat(coupon("P", Coupon.PERCENT, "10", "0", null, null, null).discountFor(new BigDecimal("33.35")))
                .isEqualByComparingTo("3.34");                       // 3.335 rounds HALF_UP
        assertThat(coupon("F", Coupon.FIXED, "5.00", "0", null, null, null).discountFor(new BigDecimal("3.00")))
                .isEqualByComparingTo("3.00");                       // capped at subtotal
    }

    @Test
    void percentAbove100IsRejectedOnCreate() {
        when(coupons.existsByCode("BIG")).thenReturn(false);
        assertThatThrownBy(() -> service.create(new CouponDtos.CreateCouponRequest(
                "big", Coupon.PERCENT, new BigDecimal("150"), null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- added after mutation testing (PIT): the usage-limit boundary, a coupon without minimum amount,
    // ---- and most of create() (duplicate code, the 100 % boundary, the date order) survived mutation.

    private static CouponDtos.CreateCouponRequest request(String code, String type, String value,
                                                          LocalDateTime from, LocalDateTime until) {
        return new CouponDtos.CreateCouponRequest(code, type, new BigDecimal(value), null, 5, from, until);
    }

    @Test
    void usageLimitIsReachedExactlyAtMaxUses() {
        Coupon c = coupon("LIMIT2", Coupon.FIXED, "1.00", "0", 2, null, null);
        when(coupons.findByCode("LIMIT2")).thenReturn(Optional.of(c));

        ReflectionTestUtils.setField(c, "usedCount", 1);
        assertThat(service.validate("LIMIT2", 9L, BigDecimal.TEN)).isSameAs(c);

        ReflectionTestUtils.setField(c, "usedCount", 2);
        assertThat(c.getUsedCount()).isEqualTo(2);
        assertThatThrownBy(() -> service.validate("LIMIT2", 9L, BigDecimal.TEN))
                .isInstanceOf(ConflictException.class).hasMessageContaining("usage limit");

        Coupon unlimited = coupon("ANY", Coupon.FIXED, "1.00", "0", null, null, null);
        ReflectionTestUtils.setField(unlimited, "usedCount", 10_000);
        assertThat(unlimited.usageLimitReached()).as("no maxUses = unlimited").isFalse();
    }

    @Test
    void couponWithoutMinimumAmountAcceptsAnyOrder() {
        Coupon c = new Coupon("FREE1", Coupon.FIXED, new BigDecimal("1.00"), null, null, null, null);
        ReflectionTestUtils.setField(c, "id", 1L);
        when(coupons.findByCode("FREE1")).thenReturn(Optional.of(c));

        assertThat(c.getMinOrderAmount()).isEqualByComparingTo("0");
        assertThat(service.validate("free1", 9L, new BigDecimal("0.01"))).isSameAs(c);
    }

    @Test
    void createStoresANormalisedCouponAndAllowsExactly100Percent() {
        when(coupons.save(any())).thenAnswer(CouponServiceTest::withId);

        Coupon c = service.create(request("  full-100 ", Coupon.PERCENT, "100", null, null));

        assertThat(c.getCode()).isEqualTo("FULL-100");
        assertThat(c.getType()).isEqualTo(Coupon.PERCENT);
        assertThat(c.getDiscountValue()).isEqualByComparingTo("100");
        assertThat(c.getMaxUses()).isEqualTo(5);
        assertThat(c.getUsedCount()).isZero();
        assertThatThrownBy(() -> service.create(request("p", Coupon.PERCENT, "100.01", null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("between 0 and 100");
        assertThat(service.create(request("f", Coupon.FIXED, "150.00", null, null)).getDiscountValue())
                .as("the 100 limit is for PERCENT only").isEqualByComparingTo("150.00");
    }

    @Test
    void createRejectsDuplicateCodeAndEndBeforeStart() {
        when(coupons.existsByCode(any())).thenAnswer(i -> "DUP".equals(i.getArgument(0)));
        assertThatThrownBy(() -> service.create(request("dup", Coupon.FIXED, "1", null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already exists");
        verify(coupons, never()).save(any());

        assertThatThrownBy(() -> service.create(request("dates", Coupon.FIXED, "1", NOW, NOW.minusSeconds(1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("validUntil");

        when(coupons.save(any())).thenAnswer(CouponServiceTest::withId);
        assertThat(service.create(request("sameday", Coupon.FIXED, "1", NOW, NOW)).getValidUntil()).isEqualTo(NOW);
        assertThat(service.create(request("open-end", Coupon.FIXED, "1", NOW, null)).getValidUntil()).isNull();
        assertThat(service.create(request("open-start", Coupon.FIXED, "1", null, NOW)).getValidFrom()).isNull();
    }

    @Test
    void listAndNormaliseHelpers() {
        Coupon c = coupon("A", Coupon.FIXED, "1.00", "0", null, null, null);
        when(coupons.findAllByOrderByIdAsc()).thenReturn(List.of(c));

        assertThat(service.all()).containsExactly(c);
        assertThat(CouponService.normalize(null)).isEmpty();
        assertThat(CouponService.normalize(" ab-1 ")).isEqualTo("AB-1");
    }

    // ---------------------------------------------------------------- admin: edit, switch off/on, search

    private static CouponDtos.UpdateCouponRequest terms(String type, String value, String min, Integer maxUses,
                                                        LocalDateTime from, LocalDateTime until) {
        return new CouponDtos.UpdateCouponRequest(type, new BigDecimal(value), min == null ? null : new BigDecimal(min),
                maxUses, from, until);
    }

    @Test
    void anEditChangesTheTermsKeepsTheCodeAndAuditsOnlyWhatChanged() {
        Coupon c = coupon("SAVE5", Coupon.FIXED, "5.00", "20.00", 10, null, null);
        when(coupons.findById(1L)).thenReturn(Optional.of(c));

        Coupon edited = service.update(1L, terms(Coupon.FIXED, "7.50", "20", 10, null, NOW.plusDays(30)));

        assertThat(edited.getCode()).isEqualTo("SAVE5");
        assertThat(edited.getDiscountValue()).isEqualByComparingTo("7.50");
        assertThat(edited.getValidUntil()).isEqualTo(NOW.plusDays(30));
        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.of(List.of(
                new com.rbctcsworld.ecommerce.audit.AuditChange("COUPON", 1L, "COUPON_UPDATED", "value", "5.00", "7.50"),
                new com.rbctcsworld.ecommerce.audit.AuditChange("COUPON", 1L, "COUPON_UPDATED", "validUntil", null,
                        NOW.plusDays(30).toString()))));
    }

    @Test
    void anEditFollowsTheSameRulesAsCreate() {
        Coupon c = coupon("SAVE5", Coupon.FIXED, "5.00", "0", 10, null, null);
        ReflectionTestUtils.setField(c, "usedCount", 4);
        when(coupons.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.update(1L, terms(Coupon.PERCENT, "100.01", null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("between 0 and 100");
        assertThatThrownBy(() -> service.update(1L, terms(Coupon.FIXED, "1", null, null, NOW, NOW.minusSeconds(1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("validUntil");
        assertThatThrownBy(() -> service.update(1L, terms(Coupon.FIXED, "1", null, 3, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("maxUses cannot be below the times already used (4)");
        assertThat(service.update(1L, terms(Coupon.FIXED, "1", null, 4, null, null)).getMaxUses()).isEqualTo(4);
        assertThat(service.update(1L, terms(Coupon.FIXED, "1", null, null, null, null)).getMinOrderAmount())
                .isEqualByComparingTo("0");
        when(coupons.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(2L, terms(Coupon.FIXED, "1", null, null, null, null)))
                .isInstanceOf(com.rbctcsworld.ecommerce.common.exception.NotFoundException.class);
    }

    @Test
    void aNewCouponIsAuditedWithItsTerms() {
        when(coupons.save(any())).thenAnswer(CouponServiceTest::withId);
        service.create(request("new5", Coupon.FIXED, "5", null, null));
        org.mockito.ArgumentCaptor<com.rbctcsworld.ecommerce.audit.AuditEvent> e =
                org.mockito.ArgumentCaptor.forClass(com.rbctcsworld.ecommerce.audit.AuditEvent.class);
        verify(events).publishEvent(e.capture());
        assertThat(e.getValue().changes()).extracting(com.rbctcsworld.ecommerce.audit.AuditChange::field)
                .containsExactly("code", "type", "value", "minOrderAmount", "maxUses", "active");
        assertThat(e.getValue().changes()).allMatch(ch -> ch.entityId() == 21L && ch.action().equals("COUPON_CREATED"));
    }

    @Test
    void switchingOffAndOnIsAuditedOnceAndRepeatingChangesNothing() {
        Coupon c = coupon("SAVE5", Coupon.FIXED, "5.00", "0", null, null, null);
        when(coupons.findById(1L)).thenReturn(Optional.of(c));

        assertThat(service.setActive(1L, false).isActive()).isFalse();
        service.setActive(1L, false);
        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.of(List.of(
                new com.rbctcsworld.ecommerce.audit.AuditChange("COUPON", 1L, "COUPON_DISABLED", "active", "true", "false"))));
        assertThat(service.setActive(1L, true).isActive()).isTrue();
        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.of(List.of(
                new com.rbctcsworld.ecommerce.audit.AuditChange("COUPON", 1L, "COUPON_ENABLED", "active", "false", "true"))));
        when(coupons.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setActive(2L, true))
                .isInstanceOf(com.rbctcsworld.ecommerce.common.exception.NotFoundException.class);
    }

    @Test
    void theStateIsWhatACustomerWouldExperienceNow() {
        Coupon active = coupon("A", Coupon.FIXED, "1", "0", 2, NOW, NOW);          // window of one instant: now
        Coupon scheduled = coupon("S", Coupon.FIXED, "1", "0", null, NOW.plusSeconds(1), null);
        Coupon expired = coupon("E", Coupon.FIXED, "1", "0", null, null, NOW.minusSeconds(1));
        Coupon usedUp = coupon("U", Coupon.FIXED, "1", "0", 2, null, null);
        ReflectionTestUtils.setField(usedUp, "usedCount", 2);
        Coupon off = coupon("D", Coupon.FIXED, "1", "0", null, null, NOW.minusSeconds(1));
        off.setActive(false);

        assertThat(List.of(active, scheduled, expired, usedUp, off)).extracting(service::state)
                .containsExactly("ACTIVE", "SCHEDULED", "EXPIRED", "USED_UP", "DISABLED");
    }

    @Test
    void searchFiltersByPartOfTheCodeAndByState() {
        Coupon save5 = coupon("SAVE5", Coupon.FIXED, "1", "0", null, null, null);
        Coupon save10 = coupon("SAVE10", Coupon.FIXED, "1", "0", null, null, NOW.minusDays(1));
        Coupon welcome = coupon("WELCOME10", Coupon.PERCENT, "10", "0", null, null, null);
        when(coupons.findAllByOrderByIdAsc()).thenReturn(List.of(save5, save10, welcome));

        assertThat(service.search(null, null)).containsExactly(save5, save10, welcome);
        assertThat(service.search(" save ", " ")).containsExactly(save5, save10);
        assertThat(service.search("10", "expired")).containsExactly(save10);
        assertThat(service.search("", "ACTIVE")).containsExactly(save5, welcome);
        assertThatThrownBy(() -> service.search(null, "OLD")).isInstanceOf(BusinessRuleException.class)
                .hasMessage("status must be one of [ACTIVE, SCHEDULED, EXPIRED, USED_UP, DISABLED]");
    }
}
