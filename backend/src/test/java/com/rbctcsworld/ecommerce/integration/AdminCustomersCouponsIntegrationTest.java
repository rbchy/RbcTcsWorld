package com.rbctcsworld.ecommerce.integration;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Store admin: customer accounts (browse, detail, disable / enable) and coupon editing, through the real API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCustomersCouponsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String email;
    private JsonNode customer;

    @BeforeEach
    void setUp() throws Exception {
        admin = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))).get("token").asString();
        email = "cust-" + UUID.randomUUID() + "@test.com";
        customer = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(login(email))).andExpect(status().isCreated()));
    }

    private static String login(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"Password1!\"}";
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long customerId() {
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    // ---------- customers ----------

    @Test
    void theAdminFindsANewCustomerByPartOfTheEmailAndSeesTheDetail() throws Exception {
        mvc.perform(as(get("/api/admin/customers?q=" + email.substring(0, 20).toUpperCase()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].email").value(email))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].orders").value(0))
                .andExpect(jsonPath("$.items[0].spent").exists())
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].password").doesNotExist());
        mvc.perform(as(get("/api/admin/customers/" + customerId()), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.email").value(email))
                .andExpect(jsonPath("$.orders.length()").value(0));
        mvc.perform(as(get("/api/admin/customers?size=100"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email").value(not(hasItem("admin@rbctcsworld.com"))));
    }

    @Test
    void aDisabledCustomerIsLockedOutAtOnceAndCanComeBackWhenEnabled() throws Exception {
        String token = customer.get("token").asString();
        String refresh = customer.get("refreshToken").asString();
        mvc.perform(as(get("/api/cart"), token)).andExpect(status().isOk());

        mvc.perform(as(post("/api/admin/customers/" + customerId() + "/disable"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(as(get("/api/cart"), token)).andExpect(status().isUnauthorized());           // token refused now
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is disabled. Please contact the store."));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CUSTOMER' and entity_id = ? "
                + "and action = 'CUSTOMER_DISABLED'", Integer.class, customerId())).isOne();

        mvc.perform(as(post("/api/admin/customers/" + customerId() + "/enable"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email)))
                .andExpect(status().isOk());
    }

    @Test
    void storeAdminsAreNotCustomersAndCustomersCannotUseTheseEndpoints() throws Exception {
        long adminId = jdbc.queryForObject("select id from users where email = 'admin@rbctcsworld.com'", Long.class);
        mvc.perform(as(post("/api/admin/customers/" + adminId + "/disable"), admin)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/admin/customers?sort=salary"), admin)).andExpect(status().isBadRequest());
        String token = customer.get("token").asString();
        mvc.perform(as(get("/api/admin/customers"), token)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/customers/" + customerId() + "/disable"), token)).andExpect(status().isForbidden());
    }

    // ---------- coupons ----------

    private long newCoupon(String code) throws Exception {
        return json(mvc.perform(as(post("/api/admin/coupons"), admin).content("{\"code\":\"" + code
                + "\",\"type\":\"FIXED\",\"value\":5.00,\"maxUses\":10}")).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void aCouponCanBeEditedSwitchedOffAndOnAndFound() throws Exception {
        String code = "ED" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long id = newCoupon(code);

        mvc.perform(as(put("/api/admin/coupons/" + id), admin)
                        .content("{\"type\":\"PERCENT\",\"value\":15,\"minOrderAmount\":25,\"maxUses\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.type").value("PERCENT"))
                .andExpect(jsonPath("$.discountValue").value(15));

        mvc.perform(as(post("/api/admin/coupons/" + id + "/disable"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(as(get("/api/admin/coupons?q=" + code.toLowerCase() + "&status=DISABLED"), admin))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(as(get("/api/admin/coupons?q=" + code + "&status=ACTIVE"), admin))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(post("/api/admin/coupons/" + id + "/enable"), admin)).andExpect(jsonPath("$.active").value(true));

        assertThat(jdbc.queryForList("select action from audit_log where entity_type = 'COUPON' and entity_id = ? "
                + "order by seq", String.class, id)).containsSubsequence("COUPON_CREATED", "COUPON_UPDATED",
                "COUPON_DISABLED", "COUPON_ENABLED");
    }

    @Test
    void couponEditsAreValidated() throws Exception {
        long id = newCoupon("EV" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        mvc.perform(as(put("/api/admin/coupons/" + id), admin).content("{\"type\":\"PERCENT\",\"value\":101}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/admin/coupons/" + id), admin).content("{\"type\":\"HALF\",\"value\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/admin/coupons/" + id), admin).content("{\"type\":\"FIXED\",\"value\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/admin/coupons/" + id), admin).content("{\"type\":\"FIXED\",\"value\":1,"
                + "\"validFrom\":\"2026-12-01T00:00:00\",\"validUntil\":\"2026-11-01T00:00:00\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/admin/coupons/999999"), admin).content("{\"type\":\"FIXED\",\"value\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/admin/coupons?status=OLD"), admin)).andExpect(status().isBadRequest());
    }
}
