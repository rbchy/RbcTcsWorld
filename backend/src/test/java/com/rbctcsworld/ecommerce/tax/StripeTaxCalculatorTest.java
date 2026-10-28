package com.rbctcsworld.ecommerce.tax;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxAddress;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxLine;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxQuote;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Stripe Tax: estimate without an address, Stripe's tax with one, and never an order with a guessed tax. */
class StripeTaxCalculatorTest {

    private static final TaxAddress PA = new TaxAddress("1 Market St", "Philadelphia", "PA", "19107");
    private final StripeTaxClient client = mock(StripeTaxClient.class);
    private final StripeTaxCalculator stripe = new StripeTaxCalculator(client, new BigDecimal("0.06"), "usd");

    private static TaxRequest request(TaxAddress to, String... amounts) {
        List<TaxLine> lines = java.util.Arrays.stream(amounts).map(a -> new TaxLine("SKU-" + a, new BigDecimal(a), 2)).toList();
        return new TaxRequest(lines, new BigDecimal("5.99"), to);
    }

    @Test
    void withoutAnAddressTheTaxIsAnEstimateAndStripeIsNotAsked() {
        TaxQuote q = stripe.calculate(request(null, "10.00"));
        assertThat(q).isEqualTo(new TaxQuote(new BigDecimal("0.60"), null, true));
        verifyNoInteractions(client);
    }

    @Test
    void withAnAddressStripeCalculatesTheTaxInCents() {
        when(client.calculate(eq("usd"), anyList(), eq(599L), eq(PA), startsWith("taxcalc-")))
                .thenReturn(new StripeTaxClient.Calculation("taxcalc_1", 1234, 21234));
        TaxQuote q = stripe.calculate(request(PA, "100.00", "100.00"));
        assertThat(q).isEqualTo(new TaxQuote(new BigDecimal("12.34"), "taxcalc_1", false));
    }

    @Test
    void linesDiscountedToZeroAreLeftOutAndNothingLeftMeansNoTax() {
        when(client.calculate(anyString(), anyList(), anyLong(), any(), anyString()))
                .thenReturn(new StripeTaxClient.Calculation("taxcalc_2", 60, 1659));
        stripe.calculate(request(PA, "0.00", "10.00"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaxLine>> sent = ArgumentCaptor.forClass(List.class);
        verify(client).calculate(eq("usd"), sent.capture(), eq(599L), eq(PA), anyString());
        assertThat(sent.getValue()).extracting(TaxLine::reference).containsExactly("SKU-10.00");

        StripeTaxClient untouched = mock(StripeTaxClient.class);
        TaxQuote none = new StripeTaxCalculator(untouched, new BigDecimal("0.06"), "usd").calculate(request(PA, "0.00"));
        assertThat(none).isEqualTo(new TaxQuote(new BigDecimal("0.00"), null, false));
        verifyNoInteractions(untouched);
    }

    @Test
    void anAddressStripeCannotPlaceIs400() {
        when(client.calculate(anyString(), anyList(), anyLong(), any(), anyString()))
                .thenThrow(new StripeTaxException(400, StripeTaxCalculator.INVALID_LOCATION, "bad"));
        assertThatThrownBy(() -> stripe.calculate(request(PA, "10.00"))).isInstanceOf(BusinessRuleException.class)
                .hasMessage(StripeTaxCalculator.BAD_ADDRESS);
    }

    @Test
    void anyOtherStripeFailureIs503SoNoOrderIsPlacedWithAGuessedTax() {
        when(client.calculate(anyString(), anyList(), anyLong(), any(), anyString()))
                .thenThrow(new StripeTaxException(0, "api_connection_error", "down"))
                .thenThrow(new StripeTaxException(400, "parameter_invalid_integer", "bad"));
        assertThatThrownBy(() -> stripe.calculate(request(PA, "10.00"))).isInstanceOf(PaymentUnavailableException.class)
                .hasMessage(StripeTaxCalculator.UNAVAILABLE);
        assertThatThrownBy(() -> stripe.calculate(request(PA, "10.00"))).isInstanceOf(PaymentUnavailableException.class);
    }

    @Test
    void aPaidOrderIsRecordedWithItsOrderNumberAsTheIdempotencyKey() {
        stripe.record("taxcalc_9", "RBC-20261027-ABC");
        verify(client).recordSale("taxcalc_9", "RBC-20261027-ABC", "tax-RBC-20261027-ABC");
        assertThat(stripe.name()).isEqualTo("stripe");
    }
}
