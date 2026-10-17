package com.rbctcsworld.ecommerce.integration;

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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Module 3: price breakdown, coupons, mock payments, refunds - API + database checks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutPaymentIntegrationTest {

    private static final String GOOD_CARD = "4242424242424242";
    private static final String DECLINED_CARD = "4000000000000002";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String customer;

    @BeforeEach
    void tokens() throws Exception {
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")));
        customer = newCustomer();
    }

    // ---------- helpers ----------

    private String token(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private String newCustomer() throws Exception {
        return token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"chk-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated()));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long product(String price, int stock) throws Exception {
        String body = mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Checkout IT\",\"sku\":\"CHK-"
                        + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":" + price + ",\"stock\":" + stock + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private void cart(String token, long productId, int qty) throws Exception {
        mvc.perform(as(post("/api/cart/items"), token).content("{\"productId\":" + productId + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions quote(String token, String coupon) throws Exception {
        return mvc.perform(as(post("/api/checkout/quote"), token)
                .content(coupon == null ? "{}" : "{\"couponCode\":\"" + coupon + "\"}"));
    }

    private ResultActions order(String token, String coupon) throws Exception {
        return mvc.perform(as(post("/api/orders"), token).content(com.rbctcsworld.ecommerce.TestAddresses.orderBody(coupon)));
    }

    private long orderId(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions pay(String token, long orderId, String card, int month, int year) throws Exception {
        return mvc.perform(as(post("/api/orders/" + orderId + "/pay"), token).content(
                "{\"cardNumber\":\"" + card + "\",\"expiryMonth\":" + month + ",\"expiryYear\":" + year + ",\"cvv\":\"123\"}"));
    }

    private int stock(long productId) {
        return jdbc.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }

    // ---------- pricing / quote ----------

    @Test
    void quoteShowsBreakdownAndChangesNothing() throws Exception {
        long p = product("10.00", 10);
        cart(customer, p, 2);

        quote(customer, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(20.00))
                .andExpect(jsonPath("$.discount").value(0))
                .andExpect(jsonPath("$.shippingFee").value(5.99))
                .andExpect(jsonPath("$.tax").value(1.20))
                .andExpect(jsonPath("$.total").value(27.19));

        assertThat(stock(p)).isEqualTo(10);
        mvc.perform(as(get("/api/cart"), customer)).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void percentCouponCanUnlockFreeShipping() throws Exception {
        long p = product("30.00", 10);
        cart(customer, p, 2);                                     // 60.00
        quote(customer, "welcome10").andExpect(status().isOk())   // lower-case accepted
                .andExpect(jsonPath("$.couponCode").value("WELCOME10"))
                .andExpect(jsonPath("$.discount").value(6.00))
                .andExpect(jsonPath("$.shippingFee").value(0))      // 54.00 >= 50
                .andExpect(jsonPath("$.tax").value(3.24))
                .andExpect(jsonPath("$.total").value(57.24));
    }

    @Test
    void couponRuleViolations() throws Exception {
        long p = product("24.99", 10);
        cart(customer, p, 1);
        quote(customer, "SAVE5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Minimum order amount")));
        quote(customer, "EXPIRED20").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("expired")));
        quote(customer, "FUTURE15").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not active yet")));
        quote(customer, "DISABLED").andExpect(status().isBadRequest());
        quote(customer, "NO-SUCH-CODE").andExpect(status().isBadRequest());
    }

    @Test
    void couponOncePerCustomerAndFailedOrderChangesNothing() throws Exception {
        long p = product("10.00", 10);
        cart(customer, p, 1);
        order(customer, "WELCOME10").andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponCode").value("WELCOME10"))
                .andExpect(jsonPath("$.discount").value(1.00));
        assertThat(stock(p)).isEqualTo(9);

        cart(customer, p, 1);
        order(customer, "WELCOME10").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already used")));
        assertThat(stock(p)).as("rolled back").isEqualTo(9);
        mvc.perform(as(get("/api/cart"), customer)).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void globalUsageLimitIsEnforced() throws Exception {
        String code = "ONE" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        mvc.perform(as(post("/api/admin/coupons"), admin)
                        .content("{\"code\":\"" + code + "\",\"type\":\"FIXED\",\"value\":2.00,\"maxUses\":1}"))
                .andExpect(status().isCreated());
        long p = product("10.00", 10);

        cart(customer, p, 1);
        order(customer, code).andExpect(status().isCreated());

        String other = newCustomer();
        cart(other, p, 1);
        order(other, code).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("usage limit")));
    }

    @Test
    void adminCouponManagementRules() throws Exception {
        String body = "{\"code\":\"X" + UUID.randomUUID().toString().substring(0, 6) + "\",\"type\":\"PERCENT\",\"value\":150}";
        mvc.perform(as(post("/api/admin/coupons"), admin).content(body)).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/admin/coupons"), customer).content(body)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/coupons"), admin)
                        .content("{\"code\":\"WELCOME10\",\"type\":\"FIXED\",\"value\":1}"))
                .andExpect(status().isConflict());
    }

    // ---------- payments ----------

    @Test
    void successfulPaymentStoresOnlyLast4AndCannotBeRepeated() throws Exception {
        long p = product("10.00", 10);
        cart(customer, p, 2);
        long id = orderId(order(customer, null));

        pay(customer, id, GOOD_CARD, 12, 2035).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").exists());

        mvc.perform(as(get("/api/orders/" + id + "/payments"), customer))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].cardLast4").value("4242"))
                .andExpect(jsonPath("$[0].amount").value(27.19));

        pay(customer, id, GOOD_CARD, 12, 2035).andExpect(status().isConflict());

        Integer sensitiveColumns = jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where lower(table_name) = 'payment_transactions' and lower(column_name) in ('card_number', 'cvv', 'pan')",
                Integer.class);
        assertThat(sensitiveColumns).as("no column can hold a full card number or CVV").isZero();
    }

    @Test
    void declinedPaymentIs402ButTheFailedAttemptIsKept() throws Exception {
        long p = product("10.00", 10);
        cart(customer, p, 1);
        long id = orderId(order(customer, null));

        pay(customer, id, DECLINED_CARD, 12, 2035).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value("Payment declined: Card declined"));

        mvc.perform(as(get("/api/orders/" + id), customer)).andExpect(jsonPath("$.status").value("PLACED"));
        Integer failed = jdbc.queryForObject(
                "select count(*) from payment_transactions where order_id = ? and status = 'FAILED'", Integer.class, id);
        assertThat(failed).as("FAILED row survives the 402 (noRollbackFor)").isEqualTo(1);

        pay(customer, id, GOOD_CARD, 12, 2035).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void invalidOrExpiredCardIs400AndNothingIsRecorded() throws Exception {
        long p = product("10.00", 10);
        cart(customer, p, 1);
        long id = orderId(order(customer, null));

        pay(customer, id, "4242424242424241", 12, 2035).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid card number"));
        pay(customer, id, GOOD_CARD, 1, 2020).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card has expired"));
        pay(customer, id, GOOD_CARD, 13, 2035).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.expiryMonth").exists());

        assertThat(jdbc.queryForObject("select count(*) from payment_transactions where order_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void cancellingPaidOrderRefundsAndRestocks() throws Exception {
        long p = product("10.00", 10);
        cart(customer, p, 3);
        long id = orderId(order(customer, null));
        pay(customer, id, GOOD_CARD, 12, 2035).andExpect(status().isOk());
        assertThat(stock(p)).isEqualTo(7);

        mvc.perform(as(post("/api/orders/" + id + "/cancel"), customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(stock(p)).isEqualTo(10);
        mvc.perform(as(get("/api/orders/" + id + "/payments"), customer))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].type").value("REFUND"))
                .andExpect(jsonPath("$[1].amount").value(37.79));   // 30.00 + 5.99 + 1.80
        pay(customer, id, GOOD_CARD, 12, 2035).andExpect(status().isConflict());
    }

    @Test
    void cannotPaySomeoneElsesOrder() throws Exception {
        long p = product("10.00", 10);
        cart(customer, p, 1);
        long id = orderId(order(customer, null));

        pay(newCustomer(), id, GOOD_CARD, 12, 2035).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/orders/" + id), customer)).andExpect(jsonPath("$.status").value("PLACED"));
    }
}
