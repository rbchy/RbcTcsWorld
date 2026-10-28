package com.rbctcsworld.ecommerce.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure calculation, no database: easy to unit-test every boundary.
 *
 *   merchandise = subtotal - discount
 *   shipping    = 0 if merchandise >= freeShippingThreshold, else shippingFee
 *   tax         = merchandise * taxRate   (shipping is not taxed)
 *   total       = merchandise + shipping + tax
 * All money is rounded HALF_UP to 2 decimals.
 */
@Service
public class PricingService {

    private final BigDecimal taxRate;
    private final BigDecimal freeShippingThreshold;
    private final BigDecimal shippingFee;

    public PricingService(@Value("${app.pricing.tax-rate}") BigDecimal taxRate,
                          @Value("${app.pricing.free-shipping-threshold}") BigDecimal freeShippingThreshold,
                          @Value("${app.pricing.shipping-fee}") BigDecimal shippingFee) {
        this.taxRate = taxRate;
        this.freeShippingThreshold = freeShippingThreshold;
        this.shippingFee = shippingFee;
    }

    public PriceBreakdown price(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal sub = money(subtotal);
        BigDecimal disc = money(discount.min(sub)); // a discount can never exceed the subtotal
        BigDecimal merchandise = sub.subtract(disc);
        BigDecimal shipping = merchandise.compareTo(freeShippingThreshold) >= 0 ? money(BigDecimal.ZERO) : money(shippingFee);
        BigDecimal tax = money(merchandise.multiply(taxRate));
        BigDecimal total = merchandise.add(shipping).add(tax);
        return new PriceBreakdown(sub, disc, shipping, tax, money(total));
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public record PriceBreakdown(BigDecimal subtotal, BigDecimal discount, BigDecimal shippingFee,
                                 BigDecimal tax, BigDecimal total) {

        /** The same prices with the tax from the tax provider (TaxCalculator): total = merchandise + shipping + tax. */
        public PriceBreakdown withTax(BigDecimal newTax) {
            BigDecimal t = money(newTax);
            return new PriceBreakdown(subtotal, discount, shippingFee, t, money(subtotal.subtract(discount).add(shippingFee).add(t)));
        }
    }
}
