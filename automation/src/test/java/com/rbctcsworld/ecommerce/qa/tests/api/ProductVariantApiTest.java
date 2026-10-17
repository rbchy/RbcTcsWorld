package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Product variants (size / colour), like Amazon's parent and child items: REQ-CAT-07..10. */
@Tag("api")
@Tag("catalog")
@Epic("Catalog")
@Feature("Product variants")
class ProductVariantApiTest {

    private final ProductClient products = new ProductClient();
    private final OrderClient orders = new OrderClient();
    private final String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    private long create(String name, String sku, String group, String size, String color, String price, int stock) {
        return products.create(Fixtures.adminToken(), ProductClient.variantBody(name, sku, new BigDecimal(price), stock, group, size, color))
                .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    @Test
    @Tag("smoke")
    @DisplayName("The seeded T-shirt: one catalog card, 8 options, 2 colours, sizes S to XL, $19.99 to $21.99")
    void seededTee() {
        List<Map<String, Object>> cards = products.search(Map.of("q", "Classic Cotton T-Shirt", "grouped", true))
                .then().statusCode(200).header("X-Total-Count", "1").extract().jsonPath().getList("$");
        long card = ((Number) cards.get(0).get("id")).longValue();

        products.variants(card).then().statusCode(200)
                .body("size()", equalTo(8))
                .body("findAll { it.color == 'Navy' }.collect { it['size'] }", contains("S", "M", "L", "XL"))
                .body("find { it.sku == 'TEE-CLS-NAV-XL' }.stock", equalTo(0));
        products.variantGroups("TEE-CLASSIC").then().statusCode(200)
                .body("[0].options", equalTo(8))
                .body("[0].colors", contains("Navy", "Heather Grey"))
                .body("[0].sizes", contains("S", "M", "L", "XL"))
                .body("[0].minPrice", equalTo(19.99f))
                .body("[0].maxPrice", equalTo(21.99f));
    }

    @Test
    @DisplayName("A variant group is built by the admin; the same size and colour twice is 409, broken fields are 400")
    void adminBuildsAGroup() {
        String group = "JKT-" + tag;
        String name = "Rain Jacket " + tag;
        create(name, group + "-Y-M", group.toLowerCase(), "M", "Yellow", "59.00", 3);
        create(name, group + "-Y-L", group, "L", "Yellow", "59.00", 2);

        products.create(Fixtures.adminToken(), ProductClient.variantBody(name, group + "-DUP", new BigDecimal("59.00"), 1, group, "m", "yellow"))
                .then().statusCode(409).body("message", containsString("already has"));
        products.create(Fixtures.adminToken(), ProductClient.variantBody(name, group + "-NOGRP", new BigDecimal("59.00"), 1, null, "S", null))
                .then().statusCode(400).body("message", equalTo("A size or colour needs a variant group"));
        products.create(Fixtures.adminToken(), ProductClient.variantBody(name, group + "-BAD", new BigDecimal("59.00"), 1, "!!", "S<", "Red1"))
                .then().statusCode(400).body("fieldErrors", hasKey("variantGroup")).body("fieldErrors", hasKey("size"))
                .body("fieldErrors", hasKey("color"));

        products.search(Map.of("q", name)).then().header("X-Total-Count", "2");
        products.search(Map.of("q", name, "grouped", true)).then().header("X-Total-Count", "1");
        products.variantGroups(group).then().body("[0].sizes", contains("M", "L")).body("[0].stock", equalTo(5));
    }

    @Test
    @DisplayName("The order line names the variant the customer bought; only that variant's stock goes down")
    void orderNamesTheVariant() {
        String group = "TOTE-" + tag;
        long green = create("Canvas Tote", group + "-G", group, null, "Green", "15.00", 4);
        long blue = create("Canvas Tote", group + "-B", group, null, "Blue", "15.00", 4);

        Customer c = Fixtures.newCustomer();
        long order = Fixtures.placedOrder(c, blue, 3);
        orders.get(c.token(), order).then().statusCode(200)
                .body("items[0].name", equalTo("Canvas Tote (Blue)"))
                .body("items[0].sku", equalTo(group + "-B"));
        assertEquals(1, products.get(blue).then().extract().jsonPath().getInt("stock"));
        assertEquals(4, products.get(green).then().extract().jsonPath().getInt("stock"));
    }

    @Test
    @DisplayName("Similar products never list another size or colour of the same item")
    void similarSkipsSiblings() {
        long tee = products.idBySku("TEE-CLS-NAV-M");
        List<String> groups = products.similar(tee, 12).then().statusCode(200).extract().jsonPath().getList("variantGroup");
        assertTrue(groups.stream().noneMatch("TEE-CLASSIC"::equals), "no other T-shirt option among: " + groups);
    }
}
