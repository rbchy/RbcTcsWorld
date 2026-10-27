package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.HostedPayment;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.HostedStatus;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.GatewayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Business mapping of Stripe answers; the HTTP layer is covered by StripeClientTest and the contract test. */
@ExtendWith(MockitoExtension.class)
class StripePaymentGatewayTest {

    private static final BigDecimal AMOUNT = new BigDecimal("24.50");

    @Mock StripeClient stripe;
    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new StripePaymentGateway(stripe, "usd");
    }

    private static StripeException cardError(String code, String declineCode) {
        return new StripeException(402, "card_error", code, declineCode, "Your card was declined.", "pi_declined");
    }

    @Test
    void approvedChargeKeepsThePaymentIntentId() {
        when(stripe.createPaymentIntent(2450, "usd", "pm_card_visa", 42L, "order-42-charge-2450-pm_card_visa"))
                .thenReturn(new StripeClient.PaymentIntent("pi_ok", "succeeded", 2450, "usd"));

        assertThat(gateway.charge(42L, "4242424242424242", AMOUNT)).isEqualTo(GatewayResult.ok("pi_ok"));
    }

    @Test
    void sameOrderAmountAndCardGiveTheSameIdempotencyKeyAnotherCardADifferentOne() {
        when(stripe.createPaymentIntent(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new StripeClient.PaymentIntent("pi_ok", "succeeded", 2450, "usd"));
        gateway.charge(7L, "5555555555554444", AMOUNT);
        verify(stripe).createPaymentIntent(2450, "usd", "pm_card_mastercard", 7L, "order-7-charge-2450-pm_card_mastercard");
    }

    @Test
    void threeDSecureIsADeclineWithAClearReason() {
        when(stripe.createPaymentIntent(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new StripeClient.PaymentIntent("pi_3ds", "requires_action", 2450, "usd"));
        assertThat(gateway.charge(1L, "4242424242424242", AMOUNT))
                .isEqualTo(GatewayResult.decline("Card needs authentication (3-D Secure)", "pi_3ds"));
    }

    @Test
    void anyOtherStatusIsNotAPayment() {
        // stripe-mock always answers "requires_payment_method": this is how the shop reads it
        when(stripe.createPaymentIntent(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new StripeClient.PaymentIntent("pi_x", "requires_payment_method", 2450, "usd"));
        assertThat(gateway.charge(1L, "4242424242424242", AMOUNT))
                .isEqualTo(GatewayResult.decline("Payment not completed (requires_payment_method)", "pi_x"));
    }

    @ParameterizedTest(name = "code={0} decline_code={1} -> {2}")
    @CsvSource(nullValues = "null", value = {
            "card_declined, insufficient_funds, Insufficient funds",
            "card_declined, generic_decline,    Card declined",
            "expired_card,  null,               Card has expired",
            "incorrect_cvc, null,               Incorrect CVC",
            "processing_error, null,            Card declined",
            "null,          null,               Card declined"})
    void cardErrorsBecomeShortCustomerSafeReasons(String code, String declineCode, String reason) {
        when(stripe.createPaymentIntent(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenThrow(cardError(code, declineCode));
        assertThat(gateway.charge(1L, "4000000000000002", AMOUNT)).isEqualTo(GatewayResult.decline(reason, "pi_declined"));
    }

    @Test
    void testCardsMapToStripeTestPaymentMethods() {
        when(stripe.createPaymentIntent(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenThrow(cardError("card_declined", "insufficient_funds"));
        gateway.charge(1L, "4000000000009995", AMOUNT);
        verify(stripe).createPaymentIntent(2450, "usd", "pm_card_chargeDeclinedInsufficientFunds", 1L,
                "order-1-charge-2450-pm_card_chargeDeclinedInsufficientFunds");
    }

    @Test
    void unknownCardNeverReachesStripe() {
        GatewayResult r = gateway.charge(1L, "4000000000000010", AMOUNT);
        assertThat(r.approved()).isFalse();
        assertThat(r.reason()).contains("4242 4242 4242 4242");
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest(name = "{0} {1} -> 503")
    @CsvSource({"0, api_connection_error", "401, invalid_request_error", "400, invalid_request_error",
                "429, invalid_request_error", "500, api_error"})
    void everythingThatIsNotAboutTheCardIs503(int status, String type) {
        when(stripe.createPaymentIntent(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new StripeException(status, type, null, null, "boom", null));
        assertThatThrownBy(() -> gateway.charge(1L, "4242424242424242", AMOUNT))
                .isInstanceOf(PaymentUnavailableException.class)
                .hasMessage(StripePaymentGateway.UNAVAILABLE);
    }

    // ---------- refunds ----------

    @ParameterizedTest
    @CsvSource({"succeeded", "pending"})
    void refundSucceedsOrIsPending(String status) {
        when(stripe.createRefund("pi_ok", 1000, 42L, "order-42-refund-2-1000"))
                .thenReturn(new StripeClient.Refund("re_1", status, 1000));
        assertThat(gateway.refund(42L, "pi_ok", new BigDecimal("10.00"), 2)).isEqualTo(GatewayResult.ok("re_1"));
    }

    @Test
    void failedRefundIsNotApproved() {
        when(stripe.createRefund(anyString(), anyLong(), any(), anyString())).thenReturn(new StripeClient.Refund("re_f", "failed", 1000));
        assertThat(gateway.refund(42L, "pi_ok", new BigDecimal("10.00"), 1)).isEqualTo(GatewayResult.decline("Refund failed", "re_f"));
    }

    @Test
    void refundErrorIs503() {
        when(stripe.createRefund(anyString(), anyLong(), any(), anyString()))
                .thenThrow(new StripeException(500, "api_error", null, null, "boom", null));
        assertThatThrownBy(() -> gateway.refund(42L, "pi_ok", AMOUNT, 1)).isInstanceOf(PaymentUnavailableException.class);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"null", "mock_ch_123", "ch_123"})
    void paymentsNotMadeThroughStripeCannotBeRefundedThere(String reference) {
        assertThatThrownBy(() -> gateway.refund(42L, reference, AMOUNT, 1))
                .isInstanceOf(PaymentUnavailableException.class).hasMessageContaining("not made through Stripe");
        verifyNoInteractions(stripe);
    }

    // ---------- small helpers ----------

    @Test
    void amountsGoToStripeInCents() {
        assertThat(StripePaymentGateway.cents(new BigDecimal("24.50"))).isEqualTo(2450);
        assertThat(StripePaymentGateway.cents(new BigDecimal("24.5"))).isEqualTo(2450);
        assertThat(StripePaymentGateway.cents(new BigDecimal("0.01"))).isEqualTo(1);
        assertThat(StripePaymentGateway.cents(new BigDecimal("1000"))).isEqualTo(100_000);
        assertThatThrownBy(() -> StripePaymentGateway.cents(new BigDecimal("19.999"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void nameIsStripe() {
        assertThat(gateway.name()).isEqualTo("stripe");
    }

    // ---------- Stripe Elements: the card goes to Stripe, this server only creates and reads the payment ----------

    private StripePaymentGateway elements(boolean live) {
        return new StripePaymentGateway(stripe, "usd", live ? "pk_live_1" : "pk_test_1", live);
    }

    @Test
    void withoutAPublishableKeyThereIsNoSecureCardForm() {
        assertThat(gateway.publishableKey()).isNull();
        assertThat(gateway.refusesCardNumbers()).isFalse();
        assertThatThrownBy(() -> gateway.startHostedPayment(42L, AMOUNT)).isInstanceOf(ConflictException.class)
                .hasMessage(PaymentGateway.HOSTED_NOT_AVAILABLE);
        verifyNoInteractions(stripe);
    }

    @Test
    void hostedPaymentIsCreatedWithoutACardOncePerOrderAndAmount() {
        when(stripe.createHostedIntent(2450, "usd", 42L, "order-42-elements-2450"))
                .thenReturn(new StripeClient.HostedIntent("pi_h", "requires_payment_method", 2450, "pi_h_secret_1"));

        StripePaymentGateway g = elements(false);

        assertThat(g.startHostedPayment(42L, AMOUNT)).isEqualTo(new HostedPayment("pi_h", "pi_h_secret_1"));
        assertThat(g.publishableKey()).isEqualTo("pk_test_1");
        assertThat(g.refusesCardNumbers()).isFalse();                          // test mode: the shop's form still works
    }

    @Test
    void hostedStartWhenStripeIsDownIs503() {
        when(stripe.createHostedIntent(anyLong(), anyString(), any(), anyString()))
                .thenThrow(new StripeException(0, "api_connection_error", null, null, "down", null));
        assertThatThrownBy(() -> elements(false).startHostedPayment(42L, AMOUNT))
                .isInstanceOf(PaymentUnavailableException.class).hasMessage(StripePaymentGateway.UNAVAILABLE);
    }

    @Test
    void hostedStatusComesFromStripeWithAShortReason() {
        when(stripe.retrieveIntent("pi_f")).thenReturn(new StripeClient.IntentState("pi_f", "requires_payment_method", 2450,
                "42", "card_declined", "insufficient_funds"));
        when(stripe.retrieveIntent("pi_ok")).thenReturn(new StripeClient.IntentState("pi_ok", "succeeded", 2450, "7", null, null));
        when(stripe.retrieveIntent("pi_x")).thenReturn(new StripeClient.IntentState("pi_x", "processing", 100, "not-ours", null, "expired_card"));

        StripePaymentGateway g = elements(false);

        HostedStatus failed = g.hostedPaymentStatus("pi_f");
        assertThat(failed).isEqualTo(new HostedStatus("pi_f", "requires_payment_method", 2450, 42L, "Insufficient funds"));
        assertThat(failed.succeeded()).isFalse();
        HostedStatus ok = g.hostedPaymentStatus("pi_ok");
        assertThat(ok).isEqualTo(new HostedStatus("pi_ok", "succeeded", 2450, 7L, null));
        assertThat(ok.succeeded()).isTrue();
        assertThat(g.hostedPaymentStatus("pi_x")).isEqualTo(new HostedStatus("pi_x", "processing", 100, null, "Card has expired"));
    }

    @Test
    void hostedStatusWhenStripeIsDownIs503() {
        when(stripe.retrieveIntent("pi_1")).thenThrow(new StripeException(500, "api_error", null, null, "boom", null));
        assertThatThrownBy(() -> elements(false).hostedPaymentStatus("pi_1")).isInstanceOf(PaymentUnavailableException.class);
    }

    @Test
    void theOrderIdIsReadFromTheMetadataOnlyWhenItIsANumber() {
        assertThat(StripePaymentGateway.orderId("42")).isEqualTo(42L);
        assertThat(StripePaymentGateway.orderId("abc")).isNull();
        assertThat(StripePaymentGateway.orderId(null)).isNull();
    }

    @Test
    void aLiveKeyRefusesCardNumbersWithoutCallingStripe() {
        StripePaymentGateway live = elements(true);

        assertThat(live.refusesCardNumbers()).isTrue();
        assertThat(live.charge(1L, "4242424242424242", AMOUNT))
                .isEqualTo(GatewayResult.decline("Enter the card in the secure card form", null));
        assertThat(live.tokenize("4242424242424242")).isNull();
        verifyNoInteractions(stripe);
    }

    @Test
    void theMockGatewayHasNoSecureCardForm() {
        MockPaymentGateway mock = new MockPaymentGateway();
        assertThat(mock.publishableKey()).isNull();
        assertThat(mock.refusesCardNumbers()).isFalse();
        assertThatThrownBy(() -> mock.startHostedPayment(1L, AMOUNT)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> mock.hostedPaymentStatus("pi_1")).isInstanceOf(ConflictException.class);
    }
}
