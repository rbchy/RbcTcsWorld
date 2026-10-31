package com.rbctcsworld.ecommerce.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Sales tax for an order. app.tax.provider chooses the implementation:
 *  - flat (default): one rate on the merchandise (app.pricing.tax-rate), the same numbers as before;
 *  - stripe: Stripe Tax, the right rate for the delivery address (state, county, city), and every paid order is
 *    recorded with Stripe so the tax report for filing is complete.
 * Shipping is passed along, so a provider can tax it where a state requires that.
 */
public interface TaxCalculator {

    /** Short name: flat | stripe. */
    String name();

    TaxQuote calculate(TaxRequest request);

    /**
     * After the order is paid: records the sale for the tax report and returns the provider's id of it (needed to
     * reverse it after a refund). Nothing to record for the flat rate: null.
     */
    default String record(String calculationId, String orderNumber) {
        return null;
    }

    /**
     * After a refund: takes the refunded part of a recorded sale out of the tax report, so the shop does not file
     * (and pay) tax it gave back. full = the whole order was refunded; otherwise refund is the amount given back,
     * tax included, spread over the sale by the provider. reference names the reversal, unique per refund.
     * Returns the provider's id of the reversal; null for the flat rate.
     */
    default String reverse(String transactionId, String reference, BigDecimal refund, boolean full) {
        return null;
    }

    /** One order line after its share of the discount; reference is the SKU; taxCode null = the provider's default. */
    record TaxLine(String reference, BigDecimal amount, int quantity, String taxCode) {

        public TaxLine(String reference, BigDecimal amount, int quantity) {
            this(reference, amount, quantity, null);
        }
    }

    record TaxAddress(String line1, String city, String state, String zip) {
    }

    /** address null: no address yet (the price preview before the address is entered). */
    record TaxRequest(List<TaxLine> lines, BigDecimal shipping, TaxAddress address) {

        public BigDecimal merchandise() {
            return lines.stream().map(TaxLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /**
         * Spreads an order discount over the lines in proportion to their amounts (a tax rate can differ per
         * product). Rounded to cents; the last line takes the rounding rest, so the lines add up to the
         * subtotal minus the discount exactly.
         */
        public static List<TaxLine> discounted(List<TaxLine> lines, BigDecimal discount) {
            BigDecimal total = lines.stream().map(TaxLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (discount.signum() == 0 || total.signum() == 0) {
                return lines;
            }
            List<TaxLine> out = new ArrayList<>();
            BigDecimal left = discount;
            for (int i = 0; i < lines.size(); i++) {
                TaxLine l = lines.get(i);
                BigDecimal share = i == lines.size() - 1 ? left
                        : discount.multiply(l.amount()).divide(total, 2, RoundingMode.HALF_UP);
                left = left.subtract(share);
                out.add(new TaxLine(l.reference(), l.amount().subtract(share), l.quantity(), l.taxCode()));
            }
            return out;
        }
    }

    /** estimated: no address yet, so the tax is a preview; calculationId: the provider's id to record after payment. */
    record TaxQuote(BigDecimal tax, String calculationId, boolean estimated) {
    }
}
