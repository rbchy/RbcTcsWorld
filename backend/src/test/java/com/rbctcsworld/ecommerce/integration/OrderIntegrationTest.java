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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Module 2: order placement + cancellation, verified through the API AND directly in the database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String customer;

    @BeforeEach
    void tokens() throws Exception {
        admin = login("admin@rbctcsworld.com", "Admin@12345");
        customer = register();
    }

    // ---------- helpers ----------

    private String register() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ord-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asString();
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long product(String price, int stock) throws Exception {
        String sku = "ORD-IT-" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(as(post("/api/products"), admin).content(
                        "{\"name\":\"Order IT\",\"sku\":\"" + sku + "\",\"category\":\"test\",\"price\":" + price + ",\"stock\":" + stock + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private void addToCart(String token, long productId, int qty) throws Exception {
        mvc.perform(as(post("/api/cart/items"), token).content("{\"productId\":" + productId + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions placeOrder(String token) throws Exception {
        return mvc.perform(as(post("/api/orders"), token).content(com.rbctcsworld.ecommerce.TestAddresses.ORDER_BODY));
    }

    private int dbStock(long productId) {
        return jdbc.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }

    // ---------- tests ----------

    @Test
    void placeOrderReducesStockSnapshotsPriceAndEmptiesCart() throws Exception {
        long a = product("10.00", 5);
        long b = product("2.50", 10);
        addToCart(customer, a, 2);
        addToCart(customer, b, 4);

        String body = placeOrder(customer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalQuantity").value(6))
                .andExpect(jsonPath("$.subtotal").value(30.00))
                .andReturn().getResponse().getContentAsString();
        long orderId = mapper.readTree(body).get("id").asLong();

        // database validation
        assertThat(dbStock(a)).isEqualTo(3);
        assertThat(dbStock(b)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id = ?", Integer.class, orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select sum(change_qty) from stock_movements where order_id = ?", Integer.class, orderId)).isEqualTo(-6);

        // cart emptied, order visible in my history
        mvc.perform(as(get("/api/cart"), customer)).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(as(get("/api/orders"), customer)).andExpect(jsonPath("$[0].id").value(orderId));

        // price snapshot: changing the product price later does not change the order
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/products/" + a), admin)
                        .content("{\"name\":\"Order IT\",\"sku\":\"CHG-" + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":999.00,\"stock\":3}"))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/orders/" + orderId), customer))
                .andExpect(jsonPath("$.subtotal").value(30.00));
    }

    @Test
    void emptyCartIs400() throws Exception {
        placeOrder(customer).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @Test
    void oneLineWithoutStockRollsBackTheWholeOrder() throws Exception {
        long plenty = product("1.00", 50);
        long scarce = product("1.00", 2);
        addToCart(customer, plenty, 3);
        addToCart(customer, scarce, 2);

        // someone else buys the scarce stock first
        String other = register();
        addToCart(other, scarce, 2);
        placeOrder(other).andExpect(status().isCreated());

        placeOrder(customer).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient stock")));

        assertThat(dbStock(plenty)).as("no partial reservation").isEqualTo(50);
        assertThat(dbStock(scarce)).isEqualTo(0);
        mvc.perform(as(get("/api/cart"), customer)).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(as(get("/api/orders"), customer)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletedProductInCartIs409() throws Exception {
        long p = product("1.00", 5);
        addToCart(customer, p, 1);
        mvc.perform(as(delete("/api/products/" + p), admin)).andExpect(status().isNoContent());

        placeOrder(customer).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no longer available")));
    }

    @Test
    void cancelRestoresStockOnceAndRecordsMovement() throws Exception {
        long p = product("5.00", 4);
        addToCart(customer, p, 3);
        long orderId = mapper.readTree(placeOrder(customer).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(dbStock(p)).isEqualTo(1);

        mvc.perform(as(post("/api/orders/" + orderId + "/cancel"), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists());
        assertThat(dbStock(p)).isEqualTo(4);

        // second cancel must not add stock again
        mvc.perform(as(post("/api/orders/" + orderId + "/cancel"), customer)).andExpect(status().isConflict());
        assertThat(dbStock(p)).isEqualTo(4);

        // audit trail via admin API: -3 then +3
        String moves = mvc.perform(as(get("/api/admin/products/" + p + "/stock-movements"), admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode m = mapper.readTree(moves);
        assertThat(m).hasSize(2);
        assertThat(m.get(0).get("changeQty").asInt()).isEqualTo(-3);
        assertThat(m.get(1).get("changeQty").asInt()).isEqualTo(3);
        assertThat(m.get(1).get("stockAfter").asInt()).isEqualTo(4);
    }

    @Test
    void customerCannotSeeOrCancelSomeoneElsesOrder_IDOR() throws Exception {
        long p = product("1.00", 5);
        addToCart(customer, p, 1);
        long orderId = mapper.readTree(placeOrder(customer).andReturn().getResponse().getContentAsString()).get("id").asLong();

        String intruder = register();
        mvc.perform(as(get("/api/orders/" + orderId), intruder)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/orders/" + orderId + "/cancel"), intruder)).andExpect(status().isNotFound());
        assertThat(dbStock(p)).isEqualTo(4);
    }

    @Test
    void anOrderNeedsADeliveryAddressAndTheCartStaysAsItWas() throws Exception {
        long p = product("10.00", 5);
        addToCart(customer, p, 1);

        mvc.perform(as(post("/api/orders"), customer).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Shipping address is required"));
        mvc.perform(as(get("/api/cart"), customer)).andExpect(jsonPath("$.items.length()").value(1));
        assertThat(dbStock(p)).isEqualTo(5);
    }

    @Test
    void anInvalidAddressNamesEveryWrongField() throws Exception {
        addToCart(customer, product("10.00", 5), 1);
        String body = "{\"shippingAddress\":{\"fullName\":\"\",\"line1\":\"12 Main St\",\"city\":\"Pittsburgh\","
                + "\"state\":\"ZZ\",\"zip\":\"ABCDE\",\"phone\":\"call me\"}}";

        mvc.perform(as(post("/api/orders"), customer).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors['shippingAddress.fullName']").exists())
                .andExpect(jsonPath("$.fieldErrors['shippingAddress.state']").value("must be a US state code such as PA"))
                .andExpect(jsonPath("$.fieldErrors['shippingAddress.zip']").value("must be a US ZIP code: 12345 or 12345-6789"))
                .andExpect(jsonPath("$.fieldErrors['shippingAddress.phone']").exists());
    }

    @Test
    void theAddressIsSavedWithTheOrderAndOnlyItsOwnerAndTheAdminSeeIt() throws Exception {
        addToCart(customer, product("10.00", 5), 1);
        String typed = "{\"shippingAddress\":{\"fullName\":\" Ada Lovelace \",\"line1\":\"12 Main St\","
                + "\"city\":\"Pittsburgh\",\"state\":\"pa\",\"zip\":\"15213-0001\"}}";
        long orderId = mapper.readTree(mvc.perform(as(post("/api/orders"), customer).content(typed))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingAddress.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.shippingAddress.state").value("PA"))
                .andExpect(jsonPath("$.shippingAddress.line2").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        assertThat(jdbc.queryForObject("select ship_state || ' ' || ship_zip from orders where id = ?", String.class, orderId))
                .isEqualTo("PA 15213-0001");
        mvc.perform(as(get("/api/orders/" + orderId), customer)).andExpect(jsonPath("$.shippingAddress.city").value("Pittsburgh"));
        mvc.perform(as(get("/api/orders/" + orderId), register())).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/admin/orders"), admin))
                .andExpect(jsonPath("$[?(@.id == " + orderId + ")].shippingAddress.zip").value(org.hamcrest.Matchers.contains("15213-0001")));
    }

    @Test
    void adminEndpointsAreAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/admin/orders"), customer)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/orders"), admin)).andExpect(status().isOk());
    }

    @Test
    void ordersRequireLogin() throws Exception {
        mvc.perform(post("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
}
