package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.TestAddresses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REQ-LEG-04 (ADR-0024): every order keeps the version of the terms and policies it was placed under. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderPolicyVersionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode json(MockHttpServletRequestBuilder r, int expected) throws Exception {
        return mapper.readTree(mvc.perform(r).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void theOrderNamesThePoliciesThePolicyPagesShowedAtCheckout() throws Exception {
        String admin = json(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"), 200).get("token").asString();
        String customer = json(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"terms-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"), 201)
                .get("token").asString();
        long product = json(as(post("/api/products"), admin).content("{\"name\":\"Terms IT\",\"sku\":\"TRM-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":10.00,\"stock\":5}"), 201).get("id").asLong();
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + product + ",\"quantity\":1}"))
                .andExpect(status().isCreated());

        JsonNode legal = json(get("/api/store/legal"), 200);
        JsonNode placed = json(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY), 201);

        assertThat(placed.path("policies").path("effective").asString()).isEqualTo(legal.path("effectiveDate").asString());
        assertThat(placed.path("policies").path("reviewed").asBoolean()).isEqualTo(legal.path("reviewed").asBoolean());
        JsonNode again = json(as(get("/api/orders/" + placed.get("id").asLong()), customer), 200);
        assertThat(again.path("policies").path("effective").asString()).isEqualTo(legal.path("effectiveDate").asString());
    }
}
