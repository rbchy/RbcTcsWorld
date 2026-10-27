package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.HostedStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stripe webhook signatures (who may change an order's payment) and how events are read. */
class StripeWebhookTest {

    private static final String SECRET = "whsec_test_secret";
    private static final long T = 1_790_000_000L;
    private static final Instant NOW = Instant.ofEpochSecond(T);
    private static final String BODY = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";

    private static void verify(String payload, String header) {
        StripeWebhook.verify(payload, header, SECRET, NOW);
    }

    @Test
    void aSignatureMadeWithTheSecretIsAccepted() {
        assertThatCode(() -> verify(BODY, StripeWebhook.signatureHeader(BODY, SECRET, T))).doesNotThrowAnyException();
    }

    @Test
    void theSignatureIsHmacSha256OfTimestampDotBody() {
        // known answer, computed outside Java: python3 -c "import hmac,hashlib;
        //   print(hmac.new(b'whsec_test_secret', b'1790000000.{}', hashlib.sha256).hexdigest())"
        assertThat(StripeWebhook.signatureHeader("{}", SECRET, T))
                .isEqualTo("t=1790000000,v1=1b80a4caddd71039bba323a0d18518a16f0f38a4a10c2deed56c9fe8f40a3d90");
    }

    @Test
    void anotherSecretOrAChangedBodyIsRefused() {
        String header = StripeWebhook.signatureHeader(BODY, "whsec_someone_else", T);
        assertThatThrownBy(() -> verify(BODY, header)).isInstanceOf(BusinessRuleException.class)
                .hasMessage(StripeWebhook.INVALID);
        String mine = StripeWebhook.signatureHeader(BODY, SECRET, T);
        assertThatThrownBy(() -> verify(BODY.replace("evt_1", "evt_2"), mine)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aTimestampMoreThanFiveMinutesAwayIsRefusedSoOldCallsCannotBeReplayed() {
        String old = StripeWebhook.signatureHeader(BODY, SECRET, T - 301);
        String future = StripeWebhook.signatureHeader(BODY, SECRET, T + 301);
        String edge = StripeWebhook.signatureHeader(BODY, SECRET, T - 300);
        assertThatThrownBy(() -> verify(BODY, old)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> verify(BODY, future)).isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> verify(BODY, edge)).doesNotThrowAnyException();
    }

    @Test
    void anyOfSeveralSignaturesMayMatchDuringASecretRollover() {
        String good = StripeWebhook.signatureHeader(BODY, SECRET, T).split(",v1=")[1];
        assertThatCode(() -> verify(BODY, "t=" + T + ",v1=not-hex,v1=" + "00".repeat(32) + ",v0=abc,v1=" + good))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "garbage", "t=abc,v1=00", "v1=00", "t=1790000000", "t=1790000000,v1=zz"})
    void malformedHeadersAreRefused(String header) {
        assertThatThrownBy(() -> verify(BODY, header)).isInstanceOf(BusinessRuleException.class)
                .hasMessage(StripeWebhook.INVALID);
    }

    @Test
    void nothingIsAcceptedWithoutHeaderBodyOrSecret() {
        String header = StripeWebhook.signatureHeader(BODY, SECRET, T);
        assertThatThrownBy(() -> verify(BODY, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> verify(null, header)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> StripeWebhook.verify(BODY, header, null, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> StripeWebhook.verify(BODY, header, " ", NOW)).isInstanceOf(BusinessRuleException.class);
    }

    // ---------- reading events ----------

    @Test
    void aSucceededPaymentIntentEventIsRead() {
        StripeWebhook.Event e = StripeWebhook.parse("""
                {"id":"evt_ok","type":"payment_intent.succeeded","data":{"object":{
                  "id":"pi_1","object":"payment_intent","amount":2719,"status":"succeeded","metadata":{"order_id":"42"}}}}""");
        assertThat(e.id()).isEqualTo("evt_ok");
        assertThat(e.type()).isEqualTo("payment_intent.succeeded");
        assertThat(e.payment()).isEqualTo(new HostedStatus("pi_1", "succeeded", 2719, 42L, null));
    }

    @Test
    void aFailedPaymentCarriesTheShortReason() {
        StripeWebhook.Event declined = StripeWebhook.parse("""
                {"id":"evt_f","type":"payment_intent.payment_failed","data":{"object":{"id":"pi_2","object":"payment_intent",
                  "amount":500,"status":"requires_payment_method","metadata":{"order_id":"7"},
                  "last_payment_error":{"code":"card_declined","decline_code":"insufficient_funds"}}}}""");
        StripeWebhook.Event expired = StripeWebhook.parse("""
                {"id":"evt_e","type":"payment_intent.payment_failed","data":{"object":{"id":"pi_3","object":"payment_intent",
                  "amount":500,"status":"requires_payment_method","last_payment_error":{"code":"expired_card"}}}}""");
        assertThat(declined.payment().failureReason()).isEqualTo("Insufficient funds");
        assertThat(declined.payment().orderId()).isEqualTo(7L);
        assertThat(expired.payment().failureReason()).isEqualTo("Card has expired");
        assertThat(expired.payment().orderId()).isNull();                 // no metadata: not a payment of this shop
    }

    @Test
    void otherEventsCarryNoPayment() {
        assertThat(StripeWebhook.parse("""
                {"id":"evt_c","type":"charge.succeeded","data":{"object":{"id":"ch_1","object":"charge"}}}""").payment()).isNull();
        assertThat(StripeWebhook.parse("""
                {"id":"evt_x","type":"payment_intent.created","data":{"object":{"id":"ch_1","object":"charge"}}}""").payment()).isNull();
        assertThat(StripeWebhook.parse("{\"id\":\"evt_n\",\"type\":\"payment_intent.created\"}").payment()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "{\"id\":\"evt_1\"}", "{\"type\":\"payment_intent.succeeded\"}", "[1,2]"})
    void aBodyThatIsNotAStripeEventIs400(String body) {
        assertThatThrownBy(() -> StripeWebhook.parse(body)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Not a Stripe event");
    }
}
