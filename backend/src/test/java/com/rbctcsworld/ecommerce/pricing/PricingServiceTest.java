package com.rbctcsworld.ecommerce.pricing;

import com.rbctcsworld.ecommerce.pricing.PricingService.PriceBreakdown;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Boundary-value tests for the money maths (tax 6%, free shipping from 50.00, fee 5.99). */
class PricingServiceTest {

    private final PricingService pricing =
            new PricingService(new BigDecimal("0.06"), new BigDecimal("50.00"), new BigDecimal("5.99"));

    @ParameterizedTest(name = "subtotal {0} - discount {1} -> shipping {2}, tax {3}, total {4}")
    @CsvSource({
            // subtotal, discount, shipping, tax,   total
            "49.99,      0,        5.99,     3.00,  58.98",   // one cent below free shipping
            "50.00,      0,        0.00,     3.00,  53.00",   // exactly at threshold -> free
            "50.01,      0,        0.00,     3.00,  53.01",
            "55.00,      5.00,     0.00,     3.00,  53.00",   // 50.00 after discount -> still free
            "54.99,      5.00,     5.99,     3.00,  58.98",   // 49.99 after discount -> pays shipping
            "10.00,      0,        5.99,     0.60,  16.59",
            "0.10,       0,        5.99,     0.01,   6.10",   // 0.006 rounds HALF_UP to 0.01
            "0.08,       0,        5.99,     0.00,   6.07"    // 0.0048 rounds to 0.00
    })
    void breakdown(String subtotal, String discount, String shipping, String tax, String total) {
        PriceBreakdown p = pricing.price(new BigDecimal(subtotal), new BigDecimal(discount));
        assertThat(p.shippingFee()).isEqualByComparingTo(shipping);
        assertThat(p.tax()).isEqualByComparingTo(tax);
        assertThat(p.total()).isEqualByComparingTo(total);
    }

    @Test
    void discountLargerThanSubtotalIsCappedSoTotalNeverGoesNegative() {
        PriceBreakdown p = pricing.price(new BigDecimal("4.00"), new BigDecimal("10.00"));
        assertThat(p.discount()).isEqualByComparingTo("4.00");
        assertThat(p.tax()).isEqualByComparingTo("0.00");
        assertThat(p.total()).isEqualByComparingTo("5.99");   // only shipping left
    }

    @Test
    void allAmountsHaveTwoDecimals() {
        PriceBreakdown p = pricing.price(new BigDecimal("33.333"), BigDecimal.ZERO);
        assertThat(p.subtotal().scale()).isEqualTo(2);
        assertThat(p.tax().scale()).isEqualTo(2);
        assertThat(p.total().scale()).isEqualTo(2);
    }

    @Test
    void anotherTaxKeepsThePricesAndRecomputesTheTotal() {
        PriceBreakdown p = pricing.price(new BigDecimal("54.99"), new BigDecimal("5.00")).withTax(new BigDecimal("4.125"));
        assertThat(p.subtotal()).isEqualByComparingTo("54.99");
        assertThat(p.discount()).isEqualByComparingTo("5.00");
        assertThat(p.shippingFee()).isEqualByComparingTo("5.99");
        assertThat(p.tax()).isEqualTo(new BigDecimal("4.13"));                 // rounded HALF_UP to cents
        assertThat(p.total()).isEqualTo(new BigDecimal("60.11"));              // 49.99 + 5.99 + 4.13
    }
}
