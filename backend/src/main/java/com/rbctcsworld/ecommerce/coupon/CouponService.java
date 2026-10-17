package com.rbctcsworld.ecommerce.coupon;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Locale;
import java.util.Map;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Coupon rules, checked in this order:
 *  1. code exists and is active                 -> else 400 "Invalid coupon code"
 *  2. now is inside valid_from .. valid_until    -> else 400 "Coupon has expired" / "Coupon is not active yet"
 *  3. subtotal >= min_order_amount              -> else 400 "Minimum order amount ..."
 *  4. this customer has not used it before       -> else 409 "Coupon already used"
 *  5. global usage limit not reached (atomic)   -> else 409 "Coupon usage limit reached"
 */
@Service
@Transactional
public class CouponService {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public CouponService(CouponRepository coupons, CouponRedemptionRepository redemptions, Clock clock,
                         ApplicationEventPublisher events) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.clock = clock;
        this.events = events;
    }

    /** Validates rules 1-4 (and a non-atomic look at rule 5) and returns the coupon. Used by quote and order. */
    @Transactional(readOnly = true)
    public Coupon validate(String rawCode, Long userId, BigDecimal subtotal) {
        String code = normalize(rawCode);
        Coupon c = coupons.findByCode(code)
                .filter(Coupon::isActive)
                .orElseThrow(() -> new BusinessRuleException("Invalid coupon code: " + code));

        LocalDateTime now = LocalDateTime.now(clock);
        if (c.getValidFrom() != null && now.isBefore(c.getValidFrom())) {
            throw new BusinessRuleException("Coupon is not active yet: " + code);
        }
        if (c.getValidUntil() != null && now.isAfter(c.getValidUntil())) {
            throw new BusinessRuleException("Coupon has expired: " + code);
        }
        if (subtotal.compareTo(c.getMinOrderAmount()) < 0) {
            throw new BusinessRuleException("Minimum order amount for " + code + " is " + c.getMinOrderAmount());
        }
        if (redemptions.existsByCouponIdAndUserId(c.getId(), userId)) {
            throw new ConflictException("Coupon already used: " + code);
        }
        if (c.usageLimitReached()) {
            throw new ConflictException("Coupon usage limit reached: " + code);
        }
        return c;
    }

    /** Records the redemption inside the order transaction; the atomic counter protects the global limit. */
    public void redeem(Coupon coupon, Long userId, Long orderId) {
        if (coupons.incrementUsage(coupon.getId()) == 0) {
            throw new ConflictException("Coupon usage limit reached: " + coupon.getCode());
        }
        redemptions.save(new CouponRedemption(coupon.getId(), userId, orderId));
    }

    public Coupon create(CouponDtos.CreateCouponRequest r) {
        String code = normalize(r.code());
        if (coupons.existsByCode(code)) {
            throw new ConflictException("Coupon code already exists: " + code);
        }
        checkTerms(r.type(), r.value(), r.validFrom(), r.validUntil());
        Coupon c = coupons.save(new Coupon(code, r.type(), r.value(), r.minOrderAmount(), r.maxUses(),
                r.validFrom(), r.validUntil()));
        events.publishEvent(AuditEvent.of(AuditChange.diff(AuditChange.COUPON, c.getId(), "COUPON_CREATED", null,
                audited(c))));
        return c;
    }

    /** New terms for an existing coupon. maxUses may not go below the times it was already used. */
    public Coupon update(long id, CouponDtos.UpdateCouponRequest r) {
        Coupon c = coupons.findById(id).orElseThrow(() -> new NotFoundException("Coupon not found: " + id));
        checkTerms(r.type(), r.value(), r.validFrom(), r.validUntil());
        if (r.maxUses() != null && r.maxUses() < c.getUsedCount()) {
            throw new BusinessRuleException("maxUses cannot be below the times already used (" + c.getUsedCount() + ")");
        }
        Map<String, Object> before = audited(c);
        c.update(r.type(), r.value(), r.minOrderAmount(), r.maxUses(), r.validFrom(), r.validUntil());
        events.publishEvent(AuditEvent.of(AuditChange.diff(AuditChange.COUPON, id, "COUPON_UPDATED", before,
                audited(c))));
        return c;
    }

    /** Switches a coupon off (customers get "Invalid coupon code") or on again. Same state: nothing recorded. */
    public Coupon setActive(long id, boolean active) {
        Coupon c = coupons.findById(id).orElseThrow(() -> new NotFoundException("Coupon not found: " + id));
        if (c.isActive() != active) {
            c.setActive(active);
            events.publishEvent(AuditEvent.of(List.of(new AuditChange(AuditChange.COUPON, id,
                    active ? "COUPON_ENABLED" : "COUPON_DISABLED", "active", Boolean.toString(!active),
                    Boolean.toString(active)))));
        }
        return c;
    }

    /**
     * Coupons for the admin list. q = part of the code; status = ACTIVE (usable now) | SCHEDULED (starts
     * later) | EXPIRED | USED_UP | DISABLED | null (all).
     */
    @Transactional(readOnly = true)
    public List<Coupon> search(String q, String status) {
        String part = q == null ? "" : normalize(q);
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (wanted != null && !STATES.contains(wanted)) {
            throw new BusinessRuleException("status must be one of " + STATES);
        }
        return coupons.findAllByOrderByIdAsc().stream()
                .filter(c -> c.getCode().contains(part))
                .filter(c -> wanted == null || wanted.equals(state(c)))
                .toList();
    }

    public static final List<String> STATES = List.of("ACTIVE", "SCHEDULED", "EXPIRED", "USED_UP", "DISABLED");

    /** What a customer would experience with this coupon right now. */
    public String state(Coupon c) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!c.isActive()) return "DISABLED";
        if (c.getValidUntil() != null && now.isAfter(c.getValidUntil())) return "EXPIRED";
        if (c.usageLimitReached()) return "USED_UP";
        if (c.getValidFrom() != null && now.isBefore(c.getValidFrom())) return "SCHEDULED";
        return "ACTIVE";
    }

    private static void checkTerms(String type, BigDecimal value, LocalDateTime from, LocalDateTime until) {
        if (Coupon.PERCENT.equals(type) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException("PERCENT coupon value must be between 0 and 100");
        }
        if (from != null && until != null && until.isBefore(from)) {
            throw new BusinessRuleException("validUntil must be after validFrom");
        }
    }

    static Map<String, Object> audited(Coupon c) {
        return AuditChange.fields("code", c.getCode(), "type", c.getType(), "value", c.getDiscountValue(),
                "minOrderAmount", c.getMinOrderAmount(), "maxUses", c.getMaxUses(), "validFrom", c.getValidFrom(),
                "validUntil", c.getValidUntil(), "active", c.isActive());
    }

    @Transactional(readOnly = true)
    public List<Coupon> all() {
        return coupons.findAllByOrderByIdAsc();
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
