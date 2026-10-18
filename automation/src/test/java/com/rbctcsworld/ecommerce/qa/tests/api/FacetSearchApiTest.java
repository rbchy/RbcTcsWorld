package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

/** Faceted search: the counts behind the catalog's filter panel, REQ-CAT-11..12. */
@Tag("api")
@Tag("catalog")
@Epic("Catalog")
@Feature("Faceted search")
class FacetSearchApiTest {

    private final ProductClient products = new ProductClient();
    private final String tag = UUID.randomUUID().toString().substring(0, 8).toLowerCase();

    @Test
    @Tag("smoke")
    @DisplayName("The seeded T-shirt is one item with 2 colours and 4 sizes, and its own colour does not hide the other colour")
    void seededTee() {
        products.facets(Map.of("q", "Classic Cotton T-Shirt")).then().statusCode(200)
                .body("total", equalTo(1))
                .body("colors.value", hasItems("Heather Grey", "Navy"))
                .body("colors.find { it.value == 'Navy' }.count", equalTo(1))
                .body("sizes.value", hasItems("S", "M", "L", "XL"));
        products.facets(Map.of("q", "Classic Cotton T-Shirt", "colors", "Navy")).then().statusCode(200)
                .body("total", equalTo(1))
                .body("colors.find { it.value == 'Heather Grey' }.count", equalTo(1));   // disjunctive: still offered
    }

    @Test
    @DisplayName("Price bands, rating steps and the in-stock count follow the other selected filters")
    void bandsAndStock() {
        String category = "fx" + tag;
        products.create(Fixtures.adminToken(), ProductClient.body("Lamp " + tag, "FXA-" + tag, category, new BigDecimal("10.00"), 5)).then().statusCode(201);
        products.create(Fixtures.adminToken(), ProductClient.body("Desk " + tag, "FXB-" + tag, category, new BigDecimal("120.00"), 0)).then().statusCode(201);

        products.facets(Map.of("category", category)).then().statusCode(200)
                .body("total", equalTo(2))
                .body("inStock", equalTo(1))
                .body("prices.find { it.key == 'under-25' }.count", equalTo(1))
                .body("prices.find { it.key == '100-200' }.count", equalTo(1))
                .body("prices.find { it.key == '50-100' }.count", equalTo(0));
        products.facets(Map.of("category", category, "inStock", true)).then().statusCode(200)
                .body("total", equalTo(1))
                .body("prices.find { it.key == '100-200' }.count", equalTo(0));
        products.search(Map.of("category", category, "minPrice", 100, "maxPrice", "199.99")).then().statusCode(200)
                .header("X-Total-Count", "1");
    }

    @Test
    @DisplayName("A rating outside 0 to 5 and more than 20 values are 400")
    void badFilters() {
        products.facets(Map.of("minRating", 6)).then().statusCode(400).body("message", equalTo("minRating must be between 0 and 5"));
        String many = String.join(",", java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(i -> "c" + i).toList());
        products.search(Map.of("colors", many)).then().statusCode(400).body("message", equalTo("at most 20 values per filter"));
    }
}
