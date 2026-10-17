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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Multi-warehouse inventory through the real API and database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WarehouseIntegrationTest {

    private static final String NY_ORDER = "{\"shippingAddress\":{\"fullName\":\"Grace Hopper\",\"line1\":\"5 Broadway\","
            + "\"city\":\"New York\",\"state\":\"NY\",\"zip\":\"10004\",\"phone\":\"212-555-0100\"}}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String customer;
    private long productId;
    private long pit;
    private long nwk;

    @BeforeEach
    void setUp() throws Exception {
        admin = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))).get("token").asString();
        customer = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wh-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))).get("token").asString();
        productId = json(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Crate\",\"sku\":\"WH-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"category\":\"wh\",\"price\":4.00,\"stock\":3}"))
                .andExpect(status().isCreated())).get("id").asLong();
        pit = jdbc.queryForObject("select id from warehouses where code = 'PIT'", Long.class);
        nwk = jdbc.queryForObject("select id from warehouses where code = 'NWK'", Long.class);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** warehouse code -> quantity of this test's product (only warehouses that hold some). */
    private Map<String, Integer> where() {
        Map<String, Integer> m = new LinkedHashMap<>();
        jdbc.query("select w.code, ws.quantity from warehouse_stock ws join warehouses w on w.id = ws.warehouse_id "
                + "where ws.product_id = ? and ws.quantity > 0 order by w.priority, w.id", rs -> {
            m.put(rs.getString(1), rs.getInt(2));
        }, productId);
        return m;
    }

    private void invariantHolds() {
        assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from warehouse_stock where product_id = ?",
                Integer.class, productId)).isEqualTo(jdbc.queryForObject("select stock from products where id = ?",
                Integer.class, productId));
    }

    private ResultActions adjust(long warehouse, int delta, String reason) throws Exception {
        return mvc.perform(as(post("/api/admin/stock/adjust"), admin).content("{\"productId\":" + productId
                + ",\"warehouseId\":" + warehouse + ",\"delta\":" + delta + ",\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions transfer(long from, long to, int qty) throws Exception {
        return mvc.perform(as(post("/api/admin/stock/transfer"), admin).content("{\"productId\":" + productId
                + ",\"fromWarehouseId\":" + from + ",\"toWarehouseId\":" + to + ",\"quantity\":" + qty + "}"));
    }

    private long order(int qty, String body) throws Exception {
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + productId + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated());
        return json(mvc.perform(as(post("/api/orders"), customer).content(body)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long newWarehouse(String state) throws Exception {
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return json(mvc.perform(as(post("/api/admin/warehouses"), admin).content("{\"code\":\"" + code
                + "\",\"name\":\"Test " + code + "\",\"city\":\"Test\",\"state\":\"" + state + "\",\"priority\":900}"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void aNewProductsStockIsInTheDefaultWarehouseAndTheTotalAlwaysMatches() throws Exception {
        assertThat(where()).containsExactly(Map.entry("PIT", 3));
        mvc.perform(as(get("/api/admin/products/" + productId + "/stock"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.warehouses[?(@.code == 'PIT')].quantity").value(org.hamcrest.Matchers.contains(3)));
        invariantHolds();

        mvc.perform(as(put("/api/products/" + productId), admin).content("{\"name\":\"Crate\",\"sku\":\""
                + jdbc.queryForObject("select sku from products where id = ?", String.class, productId)
                + "\",\"category\":\"wh\",\"price\":4.00,\"stock\":7}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(7));
        assertThat(where()).containsExactly(Map.entry("PIT", 7));
        invariantHolds();
    }

    @Test
    void aLineBiggerThanAnyWarehouseIsSplitAndCancelPutsEachUnitBackWhereItCameFrom() throws Exception {
        adjust(nwk, 2, "RECEIVED").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5));
        assertThat(where()).containsExactly(Map.entry("PIT", 3), Map.entry("NWK", 2));

        long id = order(4, com.rbctcsworld.ecommerce.TestAddresses.ORDER_BODY);          // ships to PA

        mvc.perform(as(get("/api/admin/orders/" + id + "/allocations"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].warehouseCode").value(org.hamcrest.Matchers.contains("PIT", "NWK")))
                .andExpect(jsonPath("$[*].quantity").value(org.hamcrest.Matchers.contains(3, 1)));
        assertThat(where()).containsExactly(Map.entry("NWK", 1));
        invariantHolds();

        mvc.perform(as(post("/api/orders/" + id + "/cancel"), customer)).andExpect(status().isOk());
        assertThat(where()).containsExactly(Map.entry("PIT", 3), Map.entry("NWK", 2));
        assertThat(jdbc.queryForList("select warehouse_id from stock_movements where order_id = ? order by id", Long.class, id))
                .containsExactly(pit, nwk, pit, nwk);
        invariantHolds();
    }

    @Test
    void aWarehouseInTheCustomersStateShipsFirst() throws Exception {
        long ny = newWarehouse("NY");
        transfer(pit, ny, 2).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));

        long id = order(1, NY_ORDER);

        mvc.perform(as(get("/api/admin/orders/" + id + "/allocations"), admin))
                .andExpect(jsonPath("$[0].warehouseId").value((int) ny))
                .andExpect(jsonPath("$[0].state").value("NY"));
        assertThat(jdbc.queryForObject("select quantity from warehouse_stock where warehouse_id = ? and product_id = ?",
                Integer.class, ny, productId)).isEqualTo(1);
        invariantHolds();
    }

    @Test
    void stockChangesAreValidatedAndRecorded() throws Exception {
        adjust(pit, 1, "DAMAGED").andExpect(status().isBadRequest());
        adjust(pit, -1, "RECEIVED").andExpect(status().isBadRequest());
        adjust(pit, 0, "COUNT_CORRECTION").andExpect(status().isBadRequest());
        adjust(pit, 1, "GIFT").andExpect(status().isBadRequest());
        adjust(pit, -4, "COUNT_CORRECTION").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PIT holds only 3 of this product"));
        adjust(999_999, 1, "RECEIVED").andExpect(status().isNotFound());
        transfer(pit, pit, 1).andExpect(status().isBadRequest());
        transfer(pit, nwk, 4).andExpect(status().isConflict());
        transfer(pit, nwk, 0).andExpect(status().isBadRequest());

        adjust(pit, -1, "DAMAGED").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        transfer(pit, nwk, 2).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        assertThat(where()).containsExactly(Map.entry("NWK", 2));
        assertThat(jdbc.queryForList("select reason from stock_movements where product_id = ? order by id", String.class, productId))
                .containsExactly("DAMAGED", "TRANSFER_OUT", "TRANSFER_IN");
        invariantHolds();

        // the product form cannot take units that are not in the default warehouse
        mvc.perform(as(put("/api/products/" + productId), admin).content("{\"name\":\"Crate\",\"sku\":\""
                + jdbc.queryForObject("select sku from products where id = ?", String.class, productId)
                + "\",\"category\":\"wh\",\"price\":4.00,\"stock\":0}")).andExpect(status().isBadRequest());
        invariantHolds();
    }

    @Test
    void aWarehouseIsSwitchedOffOnlyWhenEmptyAndThenReceivesNothing() throws Exception {
        long t = newWarehouse("OH");
        transfer(pit, t, 1).andExpect(status().isOk());
        String off = "{\"name\":\"Closed\",\"city\":\"Test\",\"state\":\"OH\",\"priority\":900,\"active\":false}";
        mvc.perform(as(put("/api/admin/warehouses/" + t), admin).content(off)).andExpect(status().isConflict());
        transfer(t, pit, 1).andExpect(status().isOk());
        mvc.perform(as(put("/api/admin/warehouses/" + t), admin).content(off)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        adjust(t, 1, "RECEIVED").andExpect(status().isConflict());
        transfer(pit, t, 1).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'WAREHOUSE' and entity_id = ?",
                Integer.class, t)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void warehouseInputIsValidatedAndOnlyTheAdminGetsIn() throws Exception {
        mvc.perform(as(post("/api/admin/warehouses"), admin)
                .content("{\"code\":\"PIT\",\"name\":\"Dup\",\"city\":\"X\",\"state\":\"PA\",\"priority\":5}"))
                .andExpect(status().isConflict());
        mvc.perform(as(post("/api/admin/warehouses"), admin)
                .content("{\"code\":\"!\",\"name\":\"\",\"city\":\"X\",\"state\":\"Penn\",\"priority\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/admin/warehouses/999999"), admin)
                .content("{\"name\":\"X\",\"city\":\"X\",\"state\":\"PA\",\"priority\":5,\"active\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/admin/warehouses"), customer)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/stock/adjust"), customer).content("{}")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/warehouses"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PIT"));
    }
}
