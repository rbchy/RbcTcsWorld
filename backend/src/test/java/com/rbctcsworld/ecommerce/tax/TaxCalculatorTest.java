package com.rbctcsworld.ecommerce.tax;

import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxLine;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxQuote;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The flat rate (the default, same numbers as before) and how an order discount is spread over the lines. */
class TaxCalculatorTest {

    private static TaxLine line(String sku, String amount) {
        return new TaxLine(sku, new BigDecimal(amount), 1);
    }

    @ParameterizedTest(name = "merchandise {0} -> tax {1}")
    @CsvSource({"10.00, 0.60", "0.10, 0.01", "0.08, 0.00", "49.99, 3.00", "0.00, 0.00"})
    void theFlatRateIsSixPercentOfTheMerchandiseRoundedHalfUp(String merchandise, String tax) {
        TaxQuote q = new FlatRateTaxCalculator(new BigDecimal("0.06"))
                .calculate(new TaxRequest(List.of(line("A", merchandise)), new BigDecimal("5.99"), null));
        assertThat(q.tax()).isEqualTo(new BigDecimal(tax));
        assertThat(q.calculationId()).isNull();
        assertThat(q.estimated()).isFalse();                 // the flat rate is the rate: nothing to estimate
    }

    @Test
    void theFlatRateTaxesTheLinesTogetherNotEachLineRounded() {
        // 3 x 0.10 at 6 %: per line 0.006 -> 0.01 each = 0.03; together 0.018 -> 0.02 (as the old PricingService)
        TaxRequest r = new TaxRequest(List.of(line("A", "0.10"), line("B", "0.10"), line("C", "0.10")), BigDecimal.ZERO, null);
        assertThat(new FlatRateTaxCalculator(new BigDecimal("0.06")).calculate(r).tax()).isEqualTo(new BigDecimal("0.02"));
    }

    @Test
    void theFlatRateHasANameAndNothingToRecord() {
        FlatRateTaxCalculator flat = new FlatRateTaxCalculator(new BigDecimal("0.06"));
        assertThat(flat.name()).isEqualTo("flat");
        assertThatCode(() -> flat.record("taxcalc_1", "ORD-1")).doesNotThrowAnyException();
    }

    @Test
    void aDiscountIsSpreadOverTheLinesByTheirAmountAndAddsUpExactly() {
        List<TaxLine> out = TaxRequest.discounted(List.of(line("A", "30.00"), line("B", "10.00"), line("C", "20.00")),
                new BigDecimal("10.00"));
        // shares 5.00, 1.67 (1.666..), last line the rest 3.33
        assertThat(out).extracting(TaxLine::amount)
                .containsExactly(new BigDecimal("25.00"), new BigDecimal("8.33"), new BigDecimal("16.67"));
        assertThat(out).extracting(TaxLine::reference).containsExactly("A", "B", "C");
        assertThat(new TaxRequest(out, BigDecimal.ZERO, null).merchandise()).isEqualByComparingTo("50.00");
    }

    @Test
    void theQuantityTravelsWithTheLine() {
        List<TaxLine> out = TaxRequest.discounted(List.of(new TaxLine("A", new BigDecimal("20.00"), 4)), new BigDecimal("2.00"));
        assertThat(out).containsExactly(new TaxLine("A", new BigDecimal("18.00"), 4));
    }

    @Test
    void noDiscountOrNothingToDiscountLeavesTheLinesAsTheyAre() {
        List<TaxLine> lines = List.of(line("A", "30.00"));
        assertThat(TaxRequest.discounted(lines, new BigDecimal("0.00"))).isSameAs(lines);
        List<TaxLine> free = List.of(line("A", "0.00"));
        assertThat(TaxRequest.discounted(free, new BigDecimal("1.00"))).isSameAs(free);
    }

    @Test
    void aDiscountOfTheWholeSubtotalLeavesEveryLineAtZero() {
        List<TaxLine> out = TaxRequest.discounted(List.of(line("A", "4.00"), line("B", "6.00")), new BigDecimal("10.00"));
        assertThat(out).extracting(TaxLine::amount).containsExactly(new BigDecimal("0.00"), new BigDecimal("0.00"));
    }
}
