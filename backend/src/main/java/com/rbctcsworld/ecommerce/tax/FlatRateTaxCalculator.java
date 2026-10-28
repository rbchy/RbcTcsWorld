package com.rbctcsworld.ecommerce.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One rate on the merchandise, shipping not taxed: tax = (subtotal - discount) * rate, rounded HALF_UP. */
public class FlatRateTaxCalculator implements TaxCalculator {

    private final BigDecimal rate;

    public FlatRateTaxCalculator(BigDecimal rate) {
        this.rate = rate;
    }

    @Override
    public String name() {
        return "flat";
    }

    @Override
    public TaxQuote calculate(TaxRequest request) {
        return new TaxQuote(estimate(request), null, false);
    }

    BigDecimal estimate(TaxRequest request) {
        return request.merchandise().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
