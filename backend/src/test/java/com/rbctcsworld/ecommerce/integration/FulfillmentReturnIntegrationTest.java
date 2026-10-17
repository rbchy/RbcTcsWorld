package com.rbctcsworld.ecommerce.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Module 4: the complete order lifecycle through the real API, plus database checks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FulfillmentReturnIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String customer;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")));
        customer = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ful-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}")));
        productId = json(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Lifecycle\",\"sku\":\"LC-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":10.00,\"stock\":10}"))).get("id").asLong();
    }

    // ---------- helpers ----------

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String token(ResultActions r) throws Exception {
        return json(r).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** Places an order of 2 x 10.00 (total 27.19) and returns its id. */
    private long placed() throws Exception {
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isCreated());
        return json(mvc.perform(as(post("/api/orders"), customer).content(com.rbctcsworld.ecommerce.TestAddresses.ORDER_BODY)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long paid() throws Exception {
        long id = placed();
        mvc.perform(as(post("/api/orders/" + id + "/pay"), customer)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions ship(long id) throws Exception {
        return mvc.perform(as(post("/api/admin/orders/" + id + "/ship"), admin).content("{\"carrier\":\"UPS\"}"));
    }

    private ResultActions deliver(long id) throws Exception {
        return mvc.perform(as(post("/api/admin/orders/" + id + "/deliver"), admin));
    }

    private long delivered() throws Exception {
        long id = paid();
        ship(id).andExpect(status().isOk());
        deliver(id).andExpect(status().isOk());
        return id;
    }

    private ResultActions requestReturn(long id, String reason) throws Exception {
        return mvc.perform(as(post("/api/orders/" + id + "/return"), customer).content("{\"reason\":\"" + reason + "\"}"));
    }

    private int stock() {
        return jdbc.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }

    // ---------- tests ----------

    @Test
    void fullLifecycleWithTimelineAndPublicTracking() throws Exception {
        long id = paid();
        String tracking = json(ship(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value(matchesPattern("UPS-\\d{12}")))).get("trackingNumber").asString();
        deliver(id).andExpect(status().isOk()).andExpect(jsonPath("$.deliveredAt").exists());

        mvc.perform(as(get("/api/orders/" + id + "/tracking"), customer))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.events[*].status").value(org.hamcrest.Matchers.contains("PLACED", "PAID", "SHIPPED", "DELIVERED")));

        // public tracking: no login, and no personal or price data in the response
        String publicBody = mvc.perform(get("/api/tracking/" + tracking.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(publicBody).doesNotContain("customerId", "email", "total", "price", "@");
        // ... and not the delivery address: a tracking number is printed on the parcel for anyone to see
        assertThat(publicBody).doesNotContain("shippingAddress", "Lovelace", "Main St", "15213", "412-555");

        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id = ?", Integer.class, id)).isEqualTo(4);
    }

    @Test
    void unpaidOrderCannotShipAndShippedOrderCannotBeCancelled() throws Exception {
        long unpaid = placed();
        ship(unpaid).andExpect(status().isConflict());
        deliver(unpaid).andExpect(status().isConflict());

        long id = paid();
        ship(id).andExpect(status().isOk());
        mvc.perform(as(post("/api/orders/" + id + "/cancel"), customer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SHIPPED")));
    }

    @Test
    void shippingIsAdminOnlyAndCarrierValidated() throws Exception {
        long id = paid();
        mvc.perform(as(post("/api/admin/orders/" + id + "/ship"), customer).content("{\"carrier\":\"UPS\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/orders/" + id + "/ship"), admin).content("{\"carrier\":\"DHL\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.carrier").exists());
        mvc.perform(get("/api/tracking/UPS-000000000000")).andExpect(status().isNotFound());
    }

    @Test
    void returnApprovedForWrongItemRefundsFullAndRestocks() throws Exception {
        long id = delivered();
        assertThat(stock()).isEqualTo(8);

        long returnId = json(requestReturn(id, "WRONG_ITEM").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))).get("id").asLong();
        mvc.perform(as(get("/api/orders/" + id), customer)).andExpect(jsonPath("$.status").value("RETURN_REQUESTED"));

        mvc.perform(as(post("/api/admin/returns/" + returnId + "/approve"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.refundAmount").value(27.19))
                .andExpect(jsonPath("$.restocked").value(true));

        assertThat(stock()).isEqualTo(10);
        mvc.perform(as(get("/api/orders/" + id), customer)).andExpect(jsonPath("$.status").value("RETURNED"));
        assertThat(jdbc.queryForObject("select count(*) from payment_transactions where order_id = ? and type = 'REFUND'",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where order_id = ? and reason = 'RETURN_RESTOCK'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void damagedReturnIsNotRestockedAndRemorseReturnKeepsShipping() throws Exception {
        long damaged = delivered();
        long r1 = json(requestReturn(damaged, "DAMAGED")).get("id").asLong();
        mvc.perform(as(post("/api/admin/returns/" + r1 + "/approve"), admin))
                .andExpect(jsonPath("$.refundAmount").value(27.19)).andExpect(jsonPath("$.restocked").value(false));
        assertThat(stock()).as("damaged goods are written off").isEqualTo(8);

        long remorse = delivered();
        long r2 = json(requestReturn(remorse, "NO_LONGER_NEEDED")).get("id").asLong();
        mvc.perform(as(post("/api/admin/returns/" + r2 + "/approve"), admin))
                .andExpect(jsonPath("$.refundAmount").value(21.20));   // 27.19 - 5.99 shipping
    }

    @Test
    void returnRules() throws Exception {
        long notDelivered = paid();
        requestReturn(notDelivered, "DAMAGED").andExpect(status().isConflict());

        long id = delivered();
        requestReturn(id, "BROKEN").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        long returnId = json(requestReturn(id, "NOT_AS_DESCRIBED").andExpect(status().isCreated())).get("id").asLong();
        requestReturn(id, "NOT_AS_DESCRIBED").andExpect(status().isConflict());

        mvc.perform(as(post("/api/admin/returns/" + returnId + "/reject"), admin).content("{\"note\":\"Item was used\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(as(get("/api/orders/" + id), customer)).andExpect(jsonPath("$.status").value("DELIVERED"));
        mvc.perform(as(post("/api/admin/returns/" + returnId + "/approve"), admin)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from payment_transactions where order_id = ? and type = 'REFUND'",
                Integer.class, id)).isZero();
    }

    @Test
    void anotherCustomerCannotReturnOrTrackMyOrder() throws Exception {
        long id = delivered();
        String intruder = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"intr-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}")));
        mvc.perform(as(post("/api/orders/" + id + "/return"), intruder).content("{\"reason\":\"DAMAGED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/orders/" + id + "/tracking"), intruder)).andExpect(status().isNotFound());
    }
}
