package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent;
import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type;
import com.rbctcsworld.ecommerce.tax.TaxCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A paid order's tax goes into the provider's tax report; nothing here can ever undo a payment. */
class TaxSaleRecorderTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final TaxCalculator taxes = mock(TaxCalculator.class);
    private final TaxSaleRecorder recorder = new TaxSaleRecorder(orders, taxes);

    private static CustomerOrder order(String calculationId) {
        CustomerOrder o = new CustomerOrder("RBC-20261027-ABC", 7L);
        o.recordTaxCalculation(calculationId);
        return o;
    }

    @Test
    void aPaidOrderIsRecordedWithItsCalculationAndOrderNumber() {
        when(orders.findById(5L)).thenReturn(Optional.of(order("taxcalc_1")));
        recorder.onPaid(OrderNotificationEvent.of(5L, Type.PAYMENT_RECEIVED));
        verify(taxes).record("taxcalc_1", "RBC-20261027-ABC");
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = "PAYMENT_RECEIVED", mode = EnumSource.Mode.EXCLUDE)
    void onlyAPaymentRecordsASale(Type type) {
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
