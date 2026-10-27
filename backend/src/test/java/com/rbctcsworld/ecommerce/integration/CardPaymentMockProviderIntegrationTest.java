package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.TestAddresses;
import com.rbctcsworld.ecommerce.payment.PaymentGateway;
import com.rbctcsworld.ecommerce.payment.StripeWebhook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The default setup (mock provider, no Stripe keys): the storefront keeps the shop's own card form, the secure card
 * form endpoints say so (409), and webhooks are refused until a secret is configured.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardPaymentMockProviderIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String customer() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mockform-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asString();
    }

    @Test
    void theStorefrontKeepsTheShopsOwnCardForm() throws Exception {
        mvc.perform(get("/api/payments/config").header("Authorization", "Bearer " + customer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.cardForm").value("shop"))
                .andExpect(jsonPath("$.publishableKey").doesNotExist());      // null or left out: no key for browsers
    }

    @Test
    void theSecureCardFormIsNotAvailableWithTheMockProvider() throws Exception {
        String token = customer();
        String admin = mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")).andReturn().getResponse()
                .getContentAsString()).get("token").asString();
        long product = mapper.readTree(mvc.perform(post("/api/products").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mock form\",\"sku\":\"MCK-"
                        + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":5.00,\"stock\":3}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":1}")).andExpect(status().isCreated());
        long order = mapper.readTree(mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(TestAddresses.orderBody(null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/orders/" + order + "/card-payment").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(PaymentGateway.HOSTED_NOT_AVAILABLE));
    }

    @Test
    void webhooksAreRefusedUntilASecretIsConfigured() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"type\":\"charge.succeeded\"}";
        mvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());                              // no signature at all
        mvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("Stripe-Signature", StripeWebhook.signatureHeader(payload, "whsec_any", Instant.now().getEpochSecond())))
                .andExpect(status().isServiceUnavailable());                      // nothing to check it against
    }
}
