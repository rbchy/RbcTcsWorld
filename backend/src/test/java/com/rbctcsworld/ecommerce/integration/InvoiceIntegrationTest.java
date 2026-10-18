package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The order invoice as a PDF through the real API: REQ-ORD-11. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String customer() throws Exception {
        return json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"inv-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))).get("token").asString();
    }

    @Test
    void aCustomerDownloadsThePdfOfTheirOrderAndNobodyElseCan() throws Exception {
        String admin = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))).get("token").asString();
        long product = json(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Invoice lamp (blue)\",\"sku\":\"INV-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":12.00,\"stock\":5}"))).get("id").asLong();
        String token = customer();
        mvc.perform(as(post("/api/cart/items"), token).content("{\"productId\":" + product + ",\"quantity\":2}")).andExpect(status().isCreated());
        JsonNode order = json(mvc.perform(as(post("/api/orders"), token).content("{\"shippingAddress\":{\"fullName\":\"Ada Lovelace\","
                + "\"line1\":\"1 Main St\",\"city\":\"Pittsburgh\",\"state\":\"pa\",\"zip\":\"15213\"}}")).andExpect(status().isCreated()));
        long id = order.get("id").asLong();
        String number = order.get("orderNumber").asString();

        byte[] pdf = mvc.perform(as(get("/api/orders/" + id + "/invoice"), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"invoice-" + number + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(text).startsWith("%PDF-1.4").endsWith("%%EOF\n")
                .contains("(Order " + number + ")", "(Invoice lamp \\(blue\\))", "(Ada Lovelace)", "(Pittsburgh, PA 15213)", "(Status: PLACED  \\(payment due\\))");

        // another customer gets 404 (not 403), and without a token it is 401
        mvc.perform(as(get("/api/orders/" + id + "/invoice"), customer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + id + "/invoice")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/orders/999999999/invoice"), token)).andExpect(status().isNotFound());
    }

    @Test
    void aPaidOrderSaysPaid() throws Exception {
        String admin = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))).get("token").asString();
        long product = json(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Paid lamp\",\"sku\":\"INP-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":9.50,\"stock\":5}"))).get("id").asLong();
        String token = customer();
        mvc.perform(as(post("/api/cart/items"), token).content("{\"productId\":" + product + ",\"quantity\":1}")).andExpect(status().isCreated());
        long id = json(mvc.perform(as(post("/api/orders"), token).content("{\"shippingAddress\":{\"fullName\":\"Ada Lovelace\","
                + "\"line1\":\"1 Main St\",\"city\":\"Pittsburgh\",\"state\":\"PA\",\"zip\":\"15213\"}}")).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(as(post("/api/orders/" + id + "/pay"), token)
                .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2030,\"cvv\":\"123\"}")).andExpect(status().isOk());
        String text = new String(mvc.perform(as(get("/api/orders/" + id + "/invoice"), token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1);
        assertThat(text).contains("(Status: PAID  \\(paid ").doesNotContain("payment due");
    }
}
