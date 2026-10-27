package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.TestAddresses;
import com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException;
import com.rbctcsworld.ecommerce.payment.PaymentGateway;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.HostedPayment;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.HostedStatus;
import com.rbctcsworld.ecommerce.payment.StripeWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stripe Elements end to end through the API and the database: the card never reaches the shop, the order becomes PAID
 * only when Stripe says so (confirm call or signed webhook), exactly once, and never for a wrong amount or a payment
 * the shop did not start. Stripe itself is replaced by a Mockito gateway (its HTTP layer is covered by
 * StripeClientTest and the stripe-mock contract test).
 */
@SpringBootTest(properties = "app.payment.stripe.webhook-secret=" + StripeElementsIntegrationTest.SECRET)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StripeElementsIntegrationTest {

    static final String SECRET = "whsec_integration";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentGateway stripe;

    private String admin;
    private String customer;

    @BeforeEach
    void setUp() throws Exception {
        when(stripe.name()).thenReturn("stripe");
        when(stripe.acceptsCardNumbers()).thenReturn(true);
        when(stripe.publishableKey()).thenReturn("pk_test_it");
        when(stripe.startHostedPayment(anyLong(), any())).thenAnswer(i -> new HostedPayment(
                "pi_it_" + i.getArgument(0), "pi_it_" + i.getArgument(0) + "_secret"));
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")));
        customer = register();
    }

    // ---------- helpers ----------

    private String token(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private String register() throws Exception {
        return token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"elements-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated()));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** Two items of 10.00: subtotal 20.00 + shipping 5.99 + tax 1.20 = 27.19 (2719 cents). */
    private long placedOrder(String token) throws Exception {
        String product = mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Elements IT\",\"sku\":\"ELM-"
                        + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":10.00,\"stock\":5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(as(post("/api/cart/items"), token).content("{\"productId\":" + mapper.readTree(product).get("id").asLong()
                + ",\"quantity\":2}")).andExpect(status().isCreated());
        String order = mvc.perform(as(post("/api/orders"), token).content(TestAddresses.orderBody(null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(order).get("id").asLong();
    }

    private ResultActions start(String token, long orderId) throws Exception {
        return mvc.perform(as(post("/api/orders/" + orderId + "/card-payment"), token));
    }

    private ResultActions confirm(String token, long orderId) throws Exception {
        return mvc.perform(as(post("/api/orders/" + orderId + "/card-payment/confirm"), token));
    }

    private static String event(String id, String type, String intent, long orderId, long cents, String status, String error) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"data\":{\"object\":{\"id\":\"" + intent
                + "\",\"object\":\"payment_intent\",\"amount\":" + cents + ",\"status\":\"" + status
                + "\",\"metadata\":{\"order_id\":\"" + orderId + "\"}" + (error == null ? "" : ",\"last_payment_error\":" + error) + "}}}";
    }

    private ResultActions webhook(String payload, String signature) throws Exception {
        MockHttpServletRequestBuilder b = post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload);
        return mvc.perform(signature == null ? b : b.header("Stripe-Signature", signature));
    }

    private ResultActions signed(String payload) throws Exception {
        return webhook(payload, StripeWebhook.signatureHeader(payload, SECRET, Instant.now().getEpochSecond()));
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    private int charges(long orderId, String status) {
        return jdbc.queryForObject("select count(*) from payment_transactions where order_id = ? and type = 'CHARGE' and status = ?",
                Integer.class, orderId, status);
    }

    // ---------- start: the browser gets a client secret, never a card form of ours ----------

    @Test
    void theStorefrontIsToldToUseStripesCardForm() throws Exception {
        mvc.perform(as(get("/api/payments/config"), customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("stripe"))
                .andExpect(jsonPath("$.cardForm").value("stripe-elements"))
                .andExpect(jsonPath("$.publishableKey").value("pk_test_it"));
    }

    @Test
    void startingAPaymentGivesTheClientSecretAndStoresTheIntentOnce() throws Exception {
        long id = placedOrder(customer);

        start(customer, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("pi_it_" + id + "_secret"))
                .andExpect(jsonPath("$.publishableKey").value("pk_test_it"));
        start(customer, id).andExpect(status().isOk());                      // reload of the payment page

        assertThat(jdbc.queryForObject("select count(*) from payment_intents where order_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select intent_id from payment_intents where order_id = ?", String.class, id)).isEqualTo("pi_it_" + id);
        assertThat(orderStatus(id)).isEqualTo("PLACED");                    // nothing is paid by starting
    }

    @Test
    void aNewPaymentIntentFromStripeReplacesTheOldOne() throws Exception {
        long id = placedOrder(customer);
        start(customer, id).andExpect(status().isOk());
        when(stripe.startHostedPayment(anyLong(), any())).thenReturn(new HostedPayment("pi_new_" + id, "pi_new_secret"));

        start(customer, id).andExpect(status().isOk()).andExpect(jsonPath("$.clientSecret").value("pi_new_secret"));

        assertThat(jdbc.queryForList("select intent_id from payment_intents where order_id = ?", String.class, id))
                .containsExactly("pi_new_" + id);
    }

    @Test
    void somebodyElsesOrderIs404AndAPaidOrderIs409() throws Exception {
        long id = placedOrder(customer);
        start(register(), id).andExpect(status().isNotFound());
        confirm(register(), id).andExpect(status().isNotFound());
        jdbc.update("update orders set status = 'PAID' where id = ?", id);
        start(customer, id).andExpect(status().isConflict());
    }

    @Test
    void stripeUnreachableIs503() throws Exception {
        long id = placedOrder(customer);
        when(stripe.startHostedPayment(anyLong(), any())).thenThrow(new PaymentUnavailableException("Payment service is not available"));
        start(customer, id).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from payment_intents where order_id = ?", Integer.class, id)).isZero();
    }

    // ---------- confirm: Stripe is asked, the browser is not trusted ----------

    @Test
    void confirmAfterStripeSucceededPaysTheOrderOnce() throws Exception {
        long id = placedOrder(customer);
        start(customer, id);
        when(stripe.hostedPaymentStatus("pi_it_" + id)).thenReturn(new HostedStatus("pi_it_" + id, "succeeded", 2719, id, null));

        confirm(customer, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        confirm(customer, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        signed(event("evt_late_" + id, "payment_intent.succeeded", "pi_it_" + id, id, 2719, "succeeded", null))
                .andExpect(status().isOk());

        assertThat(charges(id, "SUCCEEDED")).isEqualTo(1);                  // confirm twice + webhook = one payment
        assertThat(jdbc.queryForObject("select provider_ref from payment_transactions where order_id = ? and type = 'CHARGE'",
                String.class, id)).isEqualTo("pi_it_" + id);
        assertThat(jdbc.queryForObject("select card_last4 from payment_transactions where order_id = ? and type = 'CHARGE'",
                String.class, id)).isNull();                                // the card never reached the shop
    }

    @Test
    void aPaymentStillProcessingLeavesTheOrderPlaced() throws Exception {
        long id = placedOrder(customer);
        start(customer, id);
        when(stripe.hostedPaymentStatus("pi_it_" + id)).thenReturn(new HostedStatus("pi_it_" + id, "processing", 2719, id, null));

        confirm(customer, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLACED"));
        assertThat(charges(id, "SUCCEEDED") + charges(id, "FAILED")).isZero();
    }

    @Test
    void confirmWithoutAStartedPaymentIs409() throws Exception {
        confirm(customer, placedOrder(customer)).andExpect(status().isConflict());
    }

    @Test
    void aSucceededPaymentForAnotherAmountNeverPaysTheOrder() throws Exception {
        long id = placedOrder(customer);
        start(customer, id);
        when(stripe.hostedPaymentStatus("pi_it_" + id)).thenReturn(new HostedStatus("pi_it_" + id, "succeeded", 100, id, null));

        confirm(customer, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLACED"));

        assertThat(charges(id, "SUCCEEDED")).isZero();
        assertThat(jdbc.queryForObject("select failure_reason from payment_transactions where order_id = ? and status = 'FAILED'",
                String.class, id)).isEqualTo("Paid amount does not match the order");
    }

    // ---------- webhooks: signed by Stripe, each event once ----------

    @Test
    void aSignedSucceededEventPaysTheOrderAndARepeatChangesNothing() throws Exception {
        long id = placedOrder(customer);
        start(customer, id);
        String payload = event("evt_ok_" + id, "payment_intent.succeeded", "pi_it_" + id, id, 2719, "succeeded", null);

        signed(payload).andExpect(status().isOk()).andExpect(jsonPath("$.received").value(true));
        signed(payload).andExpect(status().isOk());                          // Stripe retries until it gets a 2xx

        assertThat(orderStatus(id)).isEqualTo("PAID");
        assertThat(charges(id, "SUCCEEDED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from stripe_events where event_id = ?", Integer.class, "evt_ok_" + id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id = ? and status = 'PAID'", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void aFailedPaymentIsRecordedAndTheOrderStaysPlaced() throws Exception {
        long id = placedOrder(customer);
        start(customer, id);

        signed(event("evt_f_" + id, "payment_intent.payment_failed", "pi_it_" + id, id, 2719, "requires_payment_method",
                "{\"code\":\"card_declined\",\"decline_code\":\"insufficient_funds\"}")).andExpect(status().isOk());

        assertThat(orderStatus(id)).isEqualTo("PLACED");
        assertThat(jdbc.queryForObject("select failure_reason from payment_transactions where order_id = ? and status = 'FAILED'",
                String.class, id)).isEqualTo("Insufficient funds");
    }

    @Test
    void eventsForPaymentsTheShopDidNotStartChangeNothing() throws Exception {
        long id = placedOrder(customer);
        start(customer, id);

        signed(event("evt_other_" + id, "payment_intent.succeeded", "pi_someone_else", id, 2719, "succeeded", null)).andExpect(status().isOk());
        signed(event("evt_noorder_" + id, "payment_intent.succeeded", "pi_x", 99999999L, 2719, "succeeded", null)).andExpect(status().isOk());
        signed("{\"id\":\"evt_nometa_" + id + "\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_it_" + id
                + "\",\"object\":\"payment_intent\",\"amount\":2719,\"status\":\"succeeded\"}}}").andExpect(status().isOk());
        signed("{\"id\":\"evt_charge_" + id + "\",\"type\":\"charge.succeeded\",\"data\":{\"object\":{\"id\":\"ch_1\",\"object\":\"charge\"}}}")
                .andExpect(status().isOk());
        signed(event("evt_created_" + id, "payment_intent.created", "pi_it_" + id, id, 2719, "requires_payment_method", null))
                .andExpect(status().isOk());

        assertThat(orderStatus(id)).isEqualTo("PLACED");
        assertThat(charges(id, "SUCCEEDED") + charges(id, "FAILED")).isZero();
    }

    @Test
    void aPaymentThatSucceedsAfterTheOrderWasCancelledIsRecordedForARefund() throws Exception {
        long id = placedOrder(customer);
        start(customer, id);
        mvc.perform(as(post("/api/orders/" + id + "/cancel"), customer)).andExpect(status().isOk());

        signed(event("evt_cx_" + id, "payment_intent.succeeded", "pi_it_" + id, id, 2719, "succeeded", null)).andExpect(status().isOk());

        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(charges(id, "SUCCEEDED")).isEqualTo(1);
    }

    @Test
    void unsignedOrBadlySignedWebhooksAre400AndChangeNothing() throws Exception {
        long id = placedOrder(customer);
        start(customer, id);
        String payload = event("evt_forged_" + id, "payment_intent.succeeded", "pi_it_" + id, id, 2719, "succeeded", null);

        webhook(payload, null).andExpect(status().isBadRequest());
        webhook(payload, StripeWebhook.signatureHeader(payload, "whsec_attacker", Instant.now().getEpochSecond()))
                .andExpect(status().isBadRequest());
        webhook(payload, StripeWebhook.signatureHeader(payload, SECRET, Instant.now().getEpochSecond() - 3600))
                .andExpect(status().isBadRequest());                         // an old, replayed call

        assertThat(orderStatus(id)).isEqualTo("PLACED");
        assertThat(jdbc.queryForObject("select count(*) from stripe_events where event_id = ?", Integer.class, "evt_forged_" + id)).isZero();
    }

    @Test
    void theWebhookNeedsNoLoginButNothingElseIsOpened() throws Exception {
        String payload = "{\"id\":\"evt_open\",\"type\":\"charge.succeeded\"}";
        signed(payload).andExpect(status().isOk());                          // Stripe has no JWT
        mvc.perform(get("/api/payments/stripe/webhook")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/1/card-payment")).andExpect(status().isUnauthorized());
        assertThat(new BigDecimal("27.19").movePointRight(2).longValueExact()).isEqualTo(2719);   // the cents used above
    }
}
