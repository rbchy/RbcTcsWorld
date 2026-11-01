package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.TestAddresses;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A customer deletes their own account: REQ-AUTH-17 (ADR-0022). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionIntegrationTest {

    private static final String PASSWORD = "Password1!";
    private static final String CARD = "{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String email;
    private String customer;
    private long userId;
    private long product;

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@rbctcsworld.com", "Admin@12345");
        email = "leaving-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        customer = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isCreated()));
        userId = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        product = mapper.readTree(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Leave IT\",\"sku\":\"DEL-"
                        + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":10.00,\"stock\":20}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String login(String user, String password) throws Exception {
        return token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user + "\",\"password\":\"" + password + "\"}")));
    }

    private String token(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions deleteAccount(String token, String password) throws Exception {
        return mvc.perform(as(post("/api/account/delete"), token).content("{\"password\":\"" + password + "\"}"));
    }

    private long order() throws Exception {
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + product + ",\"quantity\":1}"))
                .andExpect(status().isCreated());
        return mapper.readTree(mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void theAccountIsDeletedTheOrdersStayAndTheAddressCanRegisterAgain() throws Exception {
        long order = order();
        mvc.perform(as(post("/api/orders/" + order + "/pay"), customer).content(CARD)).andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/orders/" + order + "/ship"), admin).content("{\"carrier\":\"UPS\"}")).andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/orders/" + order + "/deliver"), admin)).andExpect(status().isOk());
        String address = TestAddresses.JSON.substring(TestAddresses.JSON.indexOf('{'));
        mvc.perform(as(post("/api/addresses"), customer).content("{\"label\":\"Home\",\"address\":" + address + "}"))
                .andExpect(status().isCreated());
        mvc.perform(as(post("/api/wishlist"), customer).content("{\"productId\":" + product + "}"));
        mvc.perform(as(post("/api/reviews"), customer).content("{\"productId\":" + product + ",\"rating\":1,\"title\":\"Meh\"}"))
                .andExpect(status().isCreated());
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + product + ",\"quantity\":2}"));
        assertThat(count("select rating_count from products where id = ?", product)).isEqualTo(1);

        deleteAccount(customer, PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, userId))
                .isEqualTo("deleted-" + userId + "@deleted.invalid");
        assertThat(jdbc.queryForObject("select deleted_at from users where id = ?", java.sql.Timestamp.class, userId)).isNotNull();
        for (String table : new String[] {"cart_items", "wishlist_items", "customer_addresses", "reviews", "payment_methods"}) {
            assertThat(count("select count(*) from " + table + " where user_id = ?", userId)).as(table).isZero();
        }
        assertThat(count("select rating_count from products where id = ?", product)).isZero();   // rating without the review
        assertThat(count("select count(*) from orders where id = ? and user_id = ?", order, userId)).isEqualTo(1);  // kept for tax law
        assertThat(count("select count(*) from notifications where recipient = ? and type <> 'ACCOUNT_DELETED'", email)).isZero();
        assertThat(count("select count(*) from notifications where recipient = ? and type = 'ACCOUNT_DELETED'", email)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where action = 'CUSTOMER_DELETED_ACCOUNT' and entity_id = ?", userId)).isEqualTo(1);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/orders"), customer)).andExpect(status().isUnauthorized());          // the old token is dead
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isCreated());
    }

    @Test
    void anOrderStillMovingKeepsTheAccount() throws Exception {
        order();                                                                        // PLACED, not paid
        deleteAccount(customer, PASSWORD).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, userId)).isEqualTo(email);
    }

    @Test
    void theWrongPasswordAStaffAccountAndNoLoginAreRefused() throws Exception {
        deleteAccount(customer, "Nope123!").andExpect(status().isBadRequest());
        deleteAccount(admin, "Admin@12345").andExpect(status().isBadRequest());
        mvc.perform(post("/api/account/delete").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(post("/api/account/delete"), customer).content("{}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, userId)).isEqualTo(email);
    }
}
