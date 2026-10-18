package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.PaymentDeclinedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Paying with a saved card: REQ-PAY-08. */
@ExtendWith(MockitoExtension.class)
class SavedCardChargeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
    private static final BigDecimal AMOUNT = new BigDecimal("27.19");

    @Mock PaymentTransactionRepository transactions;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(new MockPaymentGateway(), transactions, CLOCK);
    }

    private static SavedPaymentMethod saved(String token, int month, int year) {
        return new SavedPaymentMethod(7L, "Visa", "4242", month, year, "mock", token);
    }

    @Test
    void anApprovedSavedCardIsRecordedWithItsLastFourDigits() {
        when(transactions.save(any())).thenAnswer(i -> i.getArgument(0));
        PaymentTransaction tx = service.chargeSaved(1L, AMOUNT, saved("mock_pm_ok_abc", 12, 2030));
        assertThat(tx.getStatus()).isEqualTo(PaymentTransaction.SUCCEEDED);
        assertThat(tx.getCardLast4()).isEqualTo("4242");
        assertThat(tx.getProviderReference()).startsWith("mock_ch_");
    }

    @Test
    void aDecliningSavedCardIsRecordedAsFailedThen402() {
        assertThatThrownBy(() -> service.chargeSaved(1L, AMOUNT, saved("mock_pm_funds_abc", 12, 2030)))
                .isInstanceOf(PaymentDeclinedException.class).hasMessageContaining("Insufficient funds");
        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(tx.capture());
        assertThat(tx.getValue().getStatus()).isEqualTo(PaymentTransaction.FAILED);
    }

    @Test
    void anExpiredSavedCardNeverReachesTheGateway() {
        assertThatThrownBy(() -> service.chargeSaved(1L, AMOUNT, saved("mock_pm_ok_abc", 5, 2026)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Card has expired");
        verify(transactions, never()).save(any());
    }

    @Test
    void thisMonthStillCounts() {
        when(transactions.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.chargeSaved(1L, AMOUNT, saved("mock_pm_ok_abc", 6, 2026)).getStatus()).isEqualTo(PaymentTransaction.SUCCEEDED);
    }

    @Test
    void anUnknownTokenIsDeclined() {
        assertThatThrownBy(() -> service.chargeSaved(1L, AMOUNT, saved("garbage", 12, 2030)))
                .isInstanceOf(PaymentDeclinedException.class).hasMessageContaining("Unknown saved card");
    }
}
