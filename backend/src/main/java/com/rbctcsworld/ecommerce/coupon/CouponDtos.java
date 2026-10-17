package com.rbctcsworld.ecommerce.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CouponDtos {

    private CouponDtos() {
    }

    public record CreateCouponRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "letters, digits, _ and - only") String code,
            @NotBlank @Pattern(regexp = "PERCENT|FIXED", message = "must be PERCENT or FIXED") String type,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal value,
            @DecimalMin("0.00") BigDecimal minOrderAmount,
            @Min(1) Integer maxUses,
            LocalDateTime validFrom,
            LocalDateTime validUntil) {
    }

    /** Edit of an existing coupon. The code never changes (customers and old orders refer to it). */
    public record UpdateCouponRequest(
            @NotBlank @Pattern(regexp = "PERCENT|FIXED", message = "must be PERCENT or FIXED") String type,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal value,
            @DecimalMin("0.00") BigDecimal minOrderAmount,
            @Min(1) Integer maxUses,
            LocalDateTime validFrom,
            LocalDateTime validUntil) {
    }
}
