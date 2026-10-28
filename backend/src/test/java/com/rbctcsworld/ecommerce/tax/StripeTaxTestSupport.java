package com.rbctcsworld.ecommerce.tax;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/** For the contract test (another package): our real Stripe Tax requests against stripe-mock. */
public final class StripeTaxTestSupport {

    public record Calc(String id, long taxCents) {
    }

    private StripeTaxTestSupport() {
    }

    public static Calc calculate(String url, String key) {
        StripeTaxClient.Calculation c = new StripeTaxClient(url, key, Duration.ofSeconds(10)).calculate("usd",
                List.of(new TaxCalculator.TaxLine("SKU-CONTRACT", new BigDecimal("24.50"), 2)), 599,
                new TaxCalculator.TaxAddress("1 Market St", "Philadelphia", "PA", "19107"), "contract-taxcalc");
        return new Calc(c.id(), c.taxCents());
    }

    public static String recordSale(String url, String key, String calculationId) {
        return new StripeTaxClient(url, key, Duration.ofSeconds(10))
                .recordSale(calculationId, "RBC-CONTRACT-1", "contract-tax-RBC-CONTRACT-1").id();
    }
}
