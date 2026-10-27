package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.PaymentDeclinedException;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.PayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
    private static final BigDecimal AMOUNT = new BigDecimal("27.19");

    @Mock PaymentTransactionRepository transactions;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(new MockPaymentGateway(), transactions, CLOCK);
    }

    private static PayRequest card(String number, int month, int year) {
        return new PayRequest(number, month, year, "123");
    }

    @Test
    void approvedCardStoresOnlyLastFourDigits() {
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentTransaction tx = service.charge(1L, AMOUNT, card("4242 4242 4242 4242", 12, 2030));

        assertThat(tx.getStatus()).isEqualTo(PaymentTransaction.SUCCEEDED);
        assertThat(tx.getCardLast4()).isEqualTo("4242");
        assertThat(tx.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void declinedCardIsRecordedAsFailedThen402() {
        assertThatThrownBy(() -> service.charge(1L, AMOUNT, card(MockPaymentGateway.INSUFFICIENT_FUNDS_CARD, 12, 2030)))
                .isInstanceOf(PaymentDeclinedException.class).hasMessageContaining("Insufficient funds");

        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(tx.capture());
        assertThat(tx.getValue().getStatus()).isEqualTo(PaymentTransaction.FAILED);
        assertThat(tx.getValue().getFailureReason()).isEqualTo("Insufficient funds");
        assertThat(tx.getValue().getCardLast4()).isEqualTo("9995");
    }

    @ParameterizedTest(name = "invalid card {0} -> 400, never reaches gateway")
    @ValueSource(strings = {"4242424242424241", "1234567890123", "4242 4242 4242 4241", "424242424242"})
    void luhnOrLengthFailureIs400AndNothingRecorded(String number) {
        assertThatThrownBy(() -> service.charge(1L, AMOUNT, card(number, 12, 2030)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Invalid card number");
        verify(transactions, never()).save(any());
    }

    @Test
    void expiryBoundary() {
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // "now" is June 2026: a card expiring 06/2026 is still valid, 05/2026 is expired
        assertThat(service.charge(1L, AMOUNT, card("4242424242424242", 6, 2026)).getStatus())
                .isEqualTo(PaymentTransaction.SUCCEEDED);
        assertThatThrownBy(() -> service.charge(1L, AMOUNT, card("4242424242424242", 5, 2026)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Card has expired");
    }

    @Test
    void luhnAlgorithm() {
        assertThat(MockPaymentGateway.luhnValid("4242424242424242")).isTrue();
        assertThat(MockPaymentGateway.luhnValid(MockPaymentGateway.DECLINED_CARD)).isTrue();
        assertThat(MockPaymentGateway.luhnValid("4242424242424243")).isFalse();
    }

    @Test
    void payRequestNeverPrintsCardData() {
        assertThat(card("4242424242424242", 12, 2030).toString()).doesNotContain("4242").doesNotContain("123");
    }

    // added after mutation testing (PIT): only the too-short side was tested, so "13" and "19" could be
    // changed to "14" and "18" without any test noticing. All numbers below pass the Luhn check.
    @ParameterizedTest(name = "{0}-digit card -> accepted: {1}")
    @CsvSource({"12, 400000000002, false", "13, 4000000000006, true",
                "19, 4000000000000000006, true", "20, 40000000000000000002, false"})
    void cardNumberLengthMustBe13To19Digits(int length, String number, boolean accepted) {
        assertThat(number).hasSize(length);
        assertThat(MockPaymentGateway.luhnValid(number)).isTrue();
        if (accepted) {
            when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
            assertThat(service.charge(1L, AMOUNT, card(number, 12, 2030)).getStatus()).isEqualTo(PaymentTransaction.SUCCEEDED);
        } else {
            assertThatThrownBy(() -> service.charge(1L, AMOUNT, card(number, 12, 2030)))
                    .isInstanceOf(BusinessRuleException.class).hasMessage("Invalid card number");
        }
    }

    // ---------- provider reference, outages and refunds (any PaymentGateway) ----------

    private PaymentService with(PaymentGateway gateway) {
        return new PaymentService(gateway, transactions, CLOCK);
    }

    private static PaymentTransaction charged(String reference) {
        return new PaymentTransaction(1L, PaymentTransaction.CHARGE, PaymentTransaction.SUCCEEDED, AMOUNT, "4242", null,
                "stripe", reference);
    }

    @Test
    void successfulChargeKeepsTheProviderAndItsReference() {
        PaymentGateway stripe = org.mockito.Mockito.mock(PaymentGateway.class);
        when(stripe.name()).thenReturn("stripe");
        when(stripe.charge(1L, "4242424242424242", AMOUNT)).thenReturn(PaymentGateway.GatewayResult.ok("pi_123"));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentTransaction tx = with(stripe).charge(1L, AMOUNT, card("4242 4242 4242 4242", 12, 2030));

        assertThat(tx.getProvider()).isEqualTo("stripe");
        assertThat(tx.getProviderReference()).isEqualTo("pi_123");
    }

    @Test
    void declineKeepsTheProviderReferenceForSupport() {
        PaymentGateway stripe = org.mockito.Mockito.mock(PaymentGateway.class);
        when(stripe.name()).thenReturn("stripe");
        when(stripe.charge(any(), any(), any())).thenReturn(PaymentGateway.GatewayResult.decline("Card declined", "pi_9"));

        assertThatThrownBy(() -> with(stripe).charge(1L, AMOUNT, card("4242424242424242", 12, 2030)))
                .isInstanceOf(PaymentDeclinedException.class);
        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(tx.capture());
        assertThat(tx.getValue().getProviderReference()).isEqualTo("pi_9");
        assertThat(tx.getValue().getProvider()).isEqualTo("stripe");
    }

    @Test
    void providerOutageIsRecordedAndReportedAs503() {
        PaymentGateway stripe = org.mockito.Mockito.mock(PaymentGateway.class);
        when(stripe.name()).thenReturn("stripe");
        when(stripe.charge(any(), any(), any()))
                .thenThrow(new com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException("down"));

        assertThatThrownBy(() -> with(stripe).charge(1L, AMOUNT, card("4242424242424242", 12, 2030)))
                .isInstanceOf(com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException.class);
        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(tx.capture());
        assertThat(tx.getValue().getStatus()).isEqualTo(PaymentTransaction.FAILED);
        assertThat(tx.getValue().getFailureReason()).isEqualTo("Provider unavailable");
        assertThat(tx.getValue().getCardLast4()).isEqualTo("4242");
        assertThat(tx.getValue().getProviderReference()).isNull();
    }

    @Test
    void refundUsesTheChargeReferenceAndCountsEarlierRefunds() {
        PaymentGateway stripe = org.mockito.Mockito.mock(PaymentGateway.class);
        when(stripe.name()).thenReturn("stripe");
        PaymentTransaction earlierRefund = new PaymentTransaction(1L, PaymentTransaction.REFUND, PaymentTransaction.SUCCEEDED,
                new BigDecimal("5.00"), "4242", null, "stripe", "re_1");
        PaymentTransaction failedCharge = new PaymentTransaction(1L, PaymentTransaction.CHARGE, PaymentTransaction.FAILED,
                AMOUNT, "0002", "Card declined", "stripe", "pi_failed");
        when(transactions.findByOrderIdOrderByIdAsc(1L)).thenReturn(java.util.List.of(failedCharge, charged("pi_ok"), earlierRefund));
        when(stripe.refund(1L, "pi_ok", new BigDecimal("10.00"), 2)).thenReturn(PaymentGateway.GatewayResult.ok("re_2"));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentTransaction tx = with(stripe).refund(1L, new BigDecimal("10.00"));

        assertThat(tx.getType()).isEqualTo(PaymentTransaction.REFUND);
        assertThat(tx.getProviderReference()).isEqualTo("re_2");
        assertThat(tx.getCardLast4()).isEqualTo("4242");                // from the successful charge, not the failed one
        assertThat(tx.getProvider()).isEqualTo("stripe");
    }

    @Test
    void refusedRefundRollsBackAs503() {
        PaymentGateway stripe = org.mockito.Mockito.mock(PaymentGateway.class);
        when(transactions.findByOrderIdOrderByIdAsc(1L)).thenReturn(java.util.List.of(charged("pi_ok")));
        when(stripe.refund(1L, "pi_ok", AMOUNT, 1)).thenReturn(PaymentGateway.GatewayResult.decline("failed", "re_x"));

        assertThatThrownBy(() -> with(stripe).refund(1L, AMOUNT))
                .isInstanceOf(com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException.class)
                .hasMessage("Refund was not accepted: failed");
        verify(transactions, never()).save(any());
    }

    @Test
    void refundWithoutAnyChargeStillAsksTheGatewayWithoutReference() {
        PaymentGateway gateway = org.mockito.Mockito.mock(PaymentGateway.class);
        when(gateway.name()).thenReturn("mock");
        when(transactions.findByOrderIdOrderByIdAsc(1L)).thenReturn(java.util.List.of());
        when(gateway.refund(1L, null, AMOUNT, 1)).thenReturn(PaymentGateway.GatewayResult.ok("mock_re"));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(with(gateway).refund(1L, AMOUNT).getCardLast4()).isNull();
    }

    @Test
    void mockGatewayGivesReferencesAndItsName() {
        MockPaymentGateway mock = new MockPaymentGateway();
        assertThat(mock.name()).isEqualTo("mock");
        assertThat(mock.charge(1L, "4242424242424242", AMOUNT).reference()).startsWith("mock_ch_");
        assertThat(mock.charge(1L, MockPaymentGateway.DECLINED_CARD, AMOUNT).reference()).isNull();
        assertThat(mock.refund(1L, "mock_ch_1", AMOUNT, 1).reference()).startsWith("mock_re_");
    }

    // ---------- Stripe Elements / live key ----------

    /** A gateway with a live Stripe key: card numbers must never be accepted. */
    private static final class LiveGateway extends MockPaymentGateway {
        @Override
        public boolean refusesCardNumbers() {
            return true;
        }
    }

    @Test
    void aLiveKeyRefusesCardNumbersBeforeAnythingIsCharged() {
        PaymentService live = new PaymentService(new LiveGateway(), transactions, CLOCK);
        assertThatThrownBy(() -> live.charge(1L, AMOUNT, card("4242424242424242", 12, 2030)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(PaymentService.SECURE_FORM_ONLY);
        verify(transactions, never()).save(any());
    }

    @Test
    void aHostedPaymentIsRecordedWithoutAnyCardDigits() {
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentTransaction ok = service.recordHosted(5L, AMOUNT, true, "ignored when it succeeded", "pi_ok");
        PaymentTransaction failed = service.recordHosted(5L, AMOUNT, false, "Insufficient funds", "pi_f");

        assertThat(ok.getStatus()).isEqualTo(PaymentTransaction.SUCCEEDED);
        assertThat(ok.getType()).isEqualTo(PaymentTransaction.CHARGE);
        assertThat(ok.getFailureReason()).isNull();
        assertThat(ok.getCardLast4()).isNull();                         // the card never reached this server
        assertThat(ok.getProviderReference()).isEqualTo("pi_ok");
        assertThat(ok.getProvider()).isEqualTo("mock");
        assertThat(ok.getOrderId()).isEqualTo(5L);
        assertThat(ok.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(failed.getStatus()).isEqualTo(PaymentTransaction.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("Insufficient funds");
        assertThat(failed.getProviderReference()).isEqualTo("pi_f");
    }
}
