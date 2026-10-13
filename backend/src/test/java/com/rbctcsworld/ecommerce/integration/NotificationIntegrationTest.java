package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.notification.NotificationDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Order e-mails through the real HTTP layer and database: outbox rows, transactions, delivery, admin API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationDeliveryService delivery;

    private String admin;
    private String customer;
    private String email;

    @BeforeEach
    void tokens() throws Exception {
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")));
        email = "mail-" + UUID.randomUUID() + "@test.com";
        customer = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Password1!\"}")).andExpect(status().isCreated()));
    }

    private String token(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long placeOrder() throws Exception {
        String product = mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Mail IT\",\"sku\":\"MAIL-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":12.50,\"stock\":5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long productId = mapper.readTree(product).get("id").asLong();
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isCreated());
        String order = mvc.perform(as(post("/api/orders"), customer).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(order).get("id").asLong();
    }

    private ResultActions pay(long orderId, String card) throws Exception {
        return mvc.perform(as(post("/api/orders/" + orderId + "/pay"), customer).content(
                "{\"cardNumber\":\"" + card + "\",\"expiryMonth\":12,\"expiryYear\":2030,\"cvv\":\"123\"}"));
    }

    private List<Map<String, Object>> rows(long orderId) {
        return jdbc.queryForList("select type, recipient, subject, status, attempts, sent_at from notifications "
                + "where order_id = ? order by id", orderId);
    }

    private List<Object> types(long orderId) {
        return rows(orderId).stream().map(r -> r.get("type")).toList();
    }

    @Test
    void placingAndPayingAnOrderQueuesTwoEmailsForTheCustomer() throws Exception {
        long order = placeOrder();
        pay(order, "4242424242424242").andExpect(status().isOk());

        assertThat(types(order)).containsExactly("ORDER_PLACED", "PAYMENT_RECEIVED");
        assertThat(rows(order)).allSatisfy(r -> {
            assertThat(r.get("recipient")).isEqualTo(email);
            assertThat(r.get("status")).isEqualTo("PENDING");
        });
    }

    @Test
    void aDeclinedCardSendsNoPaymentEmail() throws Exception {
        long order = placeOrder();
        pay(order, "4000000000000002").andExpect(status().isPaymentRequired());
        assertThat(types(order)).containsExactly("ORDER_PLACED");     // the FAILED payment row is kept, no "paid" e-mail
    }

    @Test
    void aRefusedChangeWritesNoEmail() throws Exception {
        long order = placeOrder();
        pay(order, "4242424242424242").andExpect(status().isOk());
        pay(order, "4242424242424242").andExpect(status().isConflict());      // rolled back
        assertThat(types(order)).containsExactly("ORDER_PLACED", "PAYMENT_RECEIVED");
    }

    @Test
    void shipDeliverAndCancelAreAnnounced() throws Exception {
        long shipped = placeOrder();
        pay(shipped, "4242424242424242").andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/orders/" + shipped + "/ship"), admin).content("{\"carrier\":\"UPS\"}")).andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/orders/" + shipped + "/deliver"), admin)).andExpect(status().isOk());
        assertThat(types(shipped)).containsExactly("ORDER_PLACED", "PAYMENT_RECEIVED", "ORDER_SHIPPED", "ORDER_DELIVERED");
        assertThat(rows(shipped).get(2).get("subject").toString()).endsWith("is on its way");

        long cancelled = placeOrder();
        pay(cancelled, "4242424242424242").andExpect(status().isOk());
        mvc.perform(as(post("/api/orders/" + cancelled + "/cancel"), customer)).andExpect(status().isOk());
        assertThat(types(cancelled)).containsExactly("ORDER_PLACED", "PAYMENT_RECEIVED", "ORDER_CANCELLED");
    }

    @Test
    void theDeliveryJobSendsDueEmails() throws Exception {
        long order = placeOrder();
        while (delivery.sendDue() > 0) {                              // channel "log" in tests; other tests' rows too
            // keep going: one round sends at most one batch
        }
        assertThat(rows(order)).singleElement().satisfies(r -> {
            assertThat(r.get("status")).isEqualTo("SENT");
            assertThat(((Number) r.get("attempts")).intValue()).isEqualTo(1);
            assertThat(r.get("sent_at")).isNotNull();
        });
    }

    @Test
    void adminSeesTheOutboxWithoutTheBodyCustomersDoNot() throws Exception {
        long order = placeOrder();
        mvc.perform(as(get("/api/admin/notifications?orderId=" + order), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("ORDER_PLACED"))
                .andExpect(jsonPath("$[0].recipient").value(email))
                .andExpect(jsonPath("$[0].body").doesNotExist());
        mvc.perform(as(get("/api/admin/notifications"), customer)).andExpect(status().isForbidden());

        Long id = jdbc.queryForObject("select id from notifications where order_id = ?", Long.class, order);
        mvc.perform(as(post("/api/admin/notifications/" + id + "/retry"), admin)).andExpect(status().isConflict());
        jdbc.update("update notifications set status = 'FAILED', attempts = 5 where id = ?", id);
        mvc.perform(as(post("/api/admin/notifications/" + id + "/retry"), admin)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select status from notifications where id = ?", String.class, id)).isEqualTo("PENDING");
        mvc.perform(as(get("/api/admin/notifications?status=pending"), admin)).andExpect(status().isOk());
    }
}
