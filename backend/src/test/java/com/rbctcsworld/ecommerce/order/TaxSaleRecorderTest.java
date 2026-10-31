package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent;
import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type;
import com.rbctcsworld.ecommerce.tax.TaxCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A paid order's tax goes into the provider's tax report, a refund takes it out; nothing here can undo the money. */
class TaxSaleRecorderTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final TaxCalculator taxes = mock(TaxCalculator.class);
    private final TaxSaleRecorder recorder = new TaxSaleRecorder(orders, taxes);

    private static CustomerOrder order(String calculationId) {
        CustomerOrder o = new CustomerOrder("RBC-20261027-ABC", 7L);
        o.recordTaxCalculation(calculationId);
        return o;
    }

    private static CustomerOrder paid(String transactionId) {
        CustomerOrder o = order("taxcalc_1");
        o.applyPricing(BigDecimal.ZERO, new BigDecimal("5.99"), new BigDecimal("1.60"), new BigDecimal("27.59"), null);
        o.recordTaxTransaction(transactionId);
        return o;
    }

    private static OrderNotificationEvent refunded(Type type, String amount) {
        return new OrderNotificationEvent(5L, type, amount == null ? null : new BigDecimal(amount));
    }

    @Test
    void aPaidOrderIsRecordedWithItsCalculationAndOrderNumberAndKeepsTheSalesId() {
        CustomerOrder o = order("taxcalc_1");
        when(orders.findById(5L)).thenReturn(Optional.of(o));
        when(taxes.record("taxcalc_1", "RBC-20261027-ABC")).thenReturn("tax_sale_1");
        recorder.onPaid(OrderNotificationEvent.of(5L, Type.PAYMENT_RECEIVED));
        assertThat(o.getTaxTransactionId()).isEqualTo("tax_sale_1");
        verify(orders).save(o);
    }

    @Test
    void aWholeOrderRefundedOnCancelReversesTheWholeSale() {
        CustomerOrder o = paid("tax_sale_1");
        when(orders.findById(5L)).thenReturn(Optional.of(o));
        when(taxes.reverse("tax_sale_1", "RBC-20261027-ABC-cancel", new BigDecimal("27.59"), true)).thenReturn("tax_rev_1");
        recorder.onPaid(refunded(Type.ORDER_CANCELLED, "27.59"));
        assertThat(o.getTaxReversalId()).isEqualTo("tax_rev_1");
        verify(orders).save(o);
    }

    @Test
    void aReturnRefundedInPartReversesThatAmount() {
        CustomerOrder o = paid("tax_sale_1");
        when(orders.findById(5L)).thenReturn(Optional.of(o));
        when(taxes.reverse("tax_sale_1", "RBC-20261027-ABC-return", new BigDecimal("21.60"), false)).thenReturn("tax_rev_2");
        recorder.onPaid(refunded(Type.RETURN_APPROVED, "21.60"));
        assertThat(o.getTaxReversalId()).isEqualTo("tax_rev_2");
    }

    @Test
    void nothingIsReversedWithoutARefundTwiceOrForTheFlatRate() {
        CustomerOrder done = paid("tax_sale_1");
        done.recordTaxReversal("tax_rev_1");
        CustomerOrder flat = order(null);
        when(orders.findById(5L)).thenReturn(Optional.of(done));
        when(orders.findById(6L)).thenReturn(Optional.of(flat));
        recorder.onPaid(refunded(Type.ORDER_CANCELLED, null));                 // unpaid order cancelled
        recorder.onPaid(refunded(Type.RETURN_APPROVED, "0.00"));               // nothing given back
        recorder.onPaid(refunded(Type.ORDER_CANCELLED, "27.59"));              // already reversed
        recorder.onPaid(new OrderNotificationEvent(6L, Type.ORDER_CANCELLED, new BigDecimal("10.00")));
        recorder.onPaid(new OrderNotificationEvent(7L, Type.ORDER_CANCELLED, new BigDecimal("10.00")));  // unknown order
        verify(taxes, never()).reverse(any(), any(), any(), anyBoolean());
        verify(orders, never()).save(any());
    }

    @Test
    void aSaleThatWasNeverRecordedIsLoggedNotReversed() {
        when(orders.findById(5L)).thenReturn(Optional.of(paid(null)));
        recorder.onPaid(refunded(Type.ORDER_CANCELLED, "27.59"));
        verify(taxes, never()).reverse(any(), any(), any(), anyBoolean());
        verify(taxes).name();
    }

    @Test
    void aProviderOutageOnTheReversalNeverFailsTheRefund() {
        CustomerOrder o = paid("tax_sale_1");
        when(orders.findById(5L)).thenReturn(Optional.of(o));
        when(taxes.reverse(any(), any(), any(), anyBoolean())).thenThrow(new IllegalStateException("Stripe down"));
        assertThatCode(() -> recorder.onPaid(refunded(Type.ORDER_CANCELLED, "27.59"))).doesNotThrowAnyException();
        assertThat(o.getTaxReversalId()).isNull();
        verify(orders, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = "PAYMENT_RECEIVED", mode = EnumSource.Mode.EXCLUDE)
    void onlyAPaymentRecordsASaleAndOnlyARefundReversesOne(Type type) {
        recorder.onPaid(OrderNotificationEvent.of(5L, type));
        verifyNoInteractions(orders, taxes);
    }

    @Test
    void nothingToRecordForTheFlatRateOrAnUnknownOrder() {
        when(orders.findById(5L)).thenReturn(Optional.of(order(null)));
        when(orders.findById(6L)).thenReturn(Optional.empty());
        recorder.onPaid(OrderNotificationEvent.of(5L, Type.PAYMENT_RECEIVED));
        recorder.onPaid(OrderNotificationEvent.of(6L, Type.PAYMENT_RECEIVED));
        verify(taxes, never()).record(any(), any());
    }

    @Test
    void aProviderOutageIsLoggedAndNeverFailsThePayment() {
        when(orders.findById(5L)).thenReturn(Optional.of(order("taxcalc_1")));
        doThrow(new IllegalStateException("Stripe down")).when(taxes).record(any(), any());
        assertThatCode(() -> recorder.onPaid(OrderNotificationEvent.of(5L, Type.PAYMENT_RECEIVED))).doesNotThrowAnyException();
        verify(taxes).name();                                         // the log line names the provider
    }
}
