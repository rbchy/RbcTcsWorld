package com.rbctcsworld.ecommerce.tax;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Stripe Tax. Without an address (the price preview) the flat rate gives an estimate, marked as such; with an
 * address Stripe calculates the real tax. An address Stripe cannot place is 400; Stripe not reachable is 503, so
 * an order is never placed with a guessed tax.
 */
public class StripeTaxCalculator implements TaxCalculator {

    static final String UNAVAILABLE = "Tax calculation is not available right now. Please try again in a moment.";
    /** Stripe's error code for an address it cannot place in a tax jurisdiction. */
    static final String INVALID_LOCATION = "customer_tax_location_invalid";
    static final String BAD_ADDRESS = "The tax for this address could not be calculated. Please check the address.";
    private static final Logger log = LoggerFactory.getLogger(StripeTaxCalculator.class);

    private final StripeTaxClient stripe;
    private final FlatRateTaxCalculator estimate;
    private final String currency;

    StripeTaxCalculator(StripeTaxClient stripe, BigDecimal estimateRate, String currency) {
        this.stripe = stripe;
        this.estimate = new FlatRateTaxCalculator(estimateRate);
        this.currency = currency;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public TaxQuote calculate(TaxRequest request) {
        if (request.address() == null) {
            return new TaxQuote(estimate.estimate(request), null, true);
        }
        List<TaxLine> lines = request.lines().stream().filter(l -> l.amount().signum() > 0).toList();
        if (lines.isEmpty()) {
            return new TaxQuote(BigDecimal.ZERO.setScale(2), null, false);   // everything discounted away: nothing to tax
        }
        try {
            StripeTaxClient.Calculation c = stripe.calculate(currency, lines, StripeTaxClient.cents(request.shipping()),
                    request.address(), "taxcalc-" + UUID.randomUUID());
            return new TaxQuote(BigDecimal.valueOf(c.taxCents(), 2), c.id(), false);
        } catch (StripeTaxException e) {
            if (INVALID_LOCATION.equals(e.code())) {
                throw new BusinessRuleException(BAD_ADDRESS);
            }
            log.error("Stripe Tax failed (HTTP {} {}): {}", e.status(), e.code(), e.getMessage());
            throw new PaymentUnavailableException(UNAVAILABLE);
        }
    }

    /** Same order number = same key: a retry after a timeout never records the sale twice. */
    @Override
    public void record(String calculationId, String orderNumber) {
        stripe.recordSale(calculationId, orderNumber, "tax-" + orderNumber);
    }
}
