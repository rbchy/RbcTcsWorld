package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AdminClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.config.TestConfig;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit trail (inspired by 21 CFR Part 11 / ALCOA+): every product change and order status change is
 * recorded with who, when, old and new value; only the store admin can read it; the hash chain verifies.
 */
@Tag("api")
@Tag("audit")
@Epic("Data integrity")
@Feature("Audit trail")
class AuditApiTest {

    private final AdminClient admin = new AdminClient();
    private final ProductClient products = new ProductClient();

    @Test
    @Tag("smoke")
    @DisplayName("Price change by the admin: one row per changed field, with actor, old and new value")
    void productChangeIsAudited() {
        String sku = TestData.uniqueSku();
        long id = products.create(Fixtures.adminToken(),
                        ProductClient.body("Audit item", sku, "qa", new BigDecimal("10.00"), 5))
                .then().statusCode(201).extract().<Integer>path("id");
        products.update(Fixtures.adminToken(), id,
                ProductClient.body("Audit item", sku, "qa", new BigDecimal("12.50"), 5)).then().statusCode(200);

        JsonPath rows = admin.audit(Fixtures.adminToken(), "PRODUCT", id, null).then().statusCode(200)
                .body("[0].action", equalTo("PRODUCT_UPDATED"))
                .body("[0].field", equalTo("price"))
                .body("[0].oldValue", equalTo("10.00"))
                .body("[0].newValue", equalTo("12.50"))
                .body("[0].actor", equalTo(TestConfig.adminEmail()))
                .body("[0].hash", matchesPattern("[0-9a-f]{64}"))
                .extract().jsonPath();
        List<String> created = rows.getList("findAll { it.action == 'PRODUCT_CREATED' }.field");
        assertEquals(List.of("active", "stock", "price", "category", "sku", "name"), created,
                "creation lists every field, newest first");
    }

    @Test
    @DisplayName("Order status changes are audited with the customer and the admin as actors")
    void orderStatusIsAudited() {
        Customer c = Fixtures.newCustomer();
        long order = Fixtures.paidOrder(c, Fixtures.product("10.00", 5), 1);
        admin.ship(Fixtures.adminToken(), order, "UPS").then().statusCode(200);

        JsonPath rows = admin.audit(Fixtures.adminToken(), "ORDER", order, null).then().statusCode(200)
                .body("", hasSize(2)).extract().jsonPath();
        assertAll(
                () -> assertEquals("PAID", rows.getString("[0].oldValue")),
                () -> assertEquals("SHIPPED", rows.getString("[0].newValue")),
                () -> assertEquals(TestConfig.adminEmail(), rows.getString("[0].actor")),
                () -> assertEquals("PLACED", rows.getString("[1].oldValue")),
                () -> assertEquals("PAID", rows.getString("[1].newValue")),
                () -> assertEquals(c.email(), rows.getString("[1].actor")));
    }

    @Test
    @Tag("smoke")
    @DisplayName("Integrity check: the whole hash chain verifies")
    void chainVerifies() {
        Fixtures.product("10.00", 1);
        admin.auditVerify(Fixtures.adminToken()).then().statusCode(200)
                .body("valid", equalTo(true))
                .body("brokenAtSeq", nullValue());
    }

    @Test
    @Tag("security")
    @DisplayName("SECURITY: a customer cannot read, verify or export the audit trail")
    void customerIsRefused() {
        Customer c = Fixtures.newCustomer();
        admin.audit(c.token(), null, null, null).then().statusCode(403);
        admin.auditVerify(c.token()).then().statusCode(403);
        admin.auditExport(c.token(), "PRODUCT", 1L).then().statusCode(403);
    }

    @Test
    @Tag("security")
    @DisplayName("SECURITY: the CSV export defuses spreadsheet formulas (CSV injection)")
    void csvExportDefusesFormulas() {
        String sku = TestData.uniqueSku();
        long id = products.create(Fixtures.adminToken(),
                        ProductClient.body("=HYPERLINK(\"http://evil\")", sku, "qa", new BigDecimal("1.00"), 1))
                .then().statusCode(201).extract().<Integer>path("id");

        String csv = admin.auditExport(Fixtures.adminToken(), "PRODUCT", id).then().statusCode(200)
                .header("Content-Disposition", containsString("audit-trail.csv"))
                .extract().asString();
        assertTrue(csv.startsWith("seq,occurred_at,actor,"), "header row");
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""), "formula starts with an apostrophe: " + csv);
    }
}
