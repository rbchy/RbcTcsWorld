package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AdminClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Multi-warehouse inventory: where the stock is, which warehouse ships, and where cancelled units go. */
@Tag("api")
@Tag("inventory")
@Epic("Orders and inventory")
@Feature("Warehouses")
class WarehouseApiTest {

    private final AdminClient admin = new AdminClient();
    private final OrderClient orders = new OrderClient();

    private long warehouseId(String code) {
        List<Map<String, Object>> all = admin.warehouses(Fixtures.adminToken()).then().statusCode(200).extract().jsonPath().getList("");
        return all.stream().filter(w -> code.equals(w.get("code"))).map(w -> ((Number) w.get("id")).longValue()).findFirst().orElseThrow();
    }

    private int qty(long productId, String code) {
        JsonPath s = admin.productStock(Fixtures.adminToken(), productId).then().statusCode(200).extract().jsonPath();
        return s.getInt("warehouses.find { it.code == '" + code + "' }.quantity");
    }

    @Test
    @Tag("smoke")
    @DisplayName("An order bigger than one warehouse is split; cancel puts every unit back where it came from")
    void splitAndCancel() {
        long pit = warehouseId("PIT");
        long nwk = warehouseId("NWK");
        long p = Fixtures.product("4.00", 3);                                   // 3 in PIT (the default warehouse)
        admin.adjustStock(Fixtures.adminToken(), p, nwk, 2, "RECEIVED").then().statusCode(200).body("total", equalTo(5));

        Customer c = Fixtures.newCustomer();
        long order = Fixtures.placedOrder(c, p, 4);                              // ships to PA

        admin.allocations(Fixtures.adminToken(), order).then().statusCode(200)
                .body("warehouseCode", contains("PIT", "NWK"))
                .body("quantity", contains(3, 1));
        assertEquals(0, qty(p, "PIT"));
        assertEquals(1, qty(p, "NWK"));

        orders.cancel(c.token(), order).then().statusCode(200);
        assertEquals(3, qty(p, "PIT"));
        assertEquals(2, qty(p, "NWK"));
        assertEquals(pit, admin.allocations(Fixtures.adminToken(), order).then().extract().jsonPath().getLong("[0].warehouseId"));
    }

    @Test
    @DisplayName("Transfer and damage keep the total right; impossible moves are refused")
    void transferAndAdjust() {
        long pit = warehouseId("PIT");
        long phl = warehouseId("PHL");
        long p = Fixtures.product("4.00", 4);

        admin.transferStock(Fixtures.adminToken(), p, pit, phl, 3).then().statusCode(200).body("total", equalTo(4));
        admin.adjustStock(Fixtures.adminToken(), p, phl, -1, "DAMAGED").then().statusCode(200).body("total", equalTo(3));
        assertEquals(1, qty(p, "PIT"));
        assertEquals(2, qty(p, "PHL"));

        admin.transferStock(Fixtures.adminToken(), p, pit, phl, 2).then().statusCode(409);
        admin.adjustStock(Fixtures.adminToken(), p, pit, 1, "DAMAGED").then().statusCode(400);
        admin.transferStock(Fixtures.adminToken(), p, pit, pit, 1).then().statusCode(400);
    }

    @Test
    @DisplayName("A new warehouse is validated; its code is unique")
    void newWarehouse() {
        String code = "Q" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        admin.createWarehouse(Fixtures.adminToken(), Map.of("code", code, "name", "QA hub", "city", "Albany", "state", "ny",
                "priority", 950)).then().statusCode(201).body("state", equalTo("NY")).body("units", equalTo(0));
        admin.createWarehouse(Fixtures.adminToken(), Map.of("code", code, "name", "Dup", "city", "X", "state", "NY",
                "priority", 950)).then().statusCode(409);
        admin.createWarehouse(Fixtures.adminToken(), Map.of("code", "!", "name", "", "city", "X", "state", "York",
                "priority", 0)).then().statusCode(400);
    }

    @Test
    @Tag("security")
    @DisplayName("SECURITY: customers cannot see or move stock")
    void customersAreRefused() {
        Customer c = Fixtures.newCustomer();
        admin.warehouses(c.token()).then().statusCode(403);
        admin.adjustStock(c.token(), 1, 1, 1, "RECEIVED").then().statusCode(403);
        admin.transferStock(c.token(), 1, 1, 2, 1).then().statusCode(403);
    }
}
