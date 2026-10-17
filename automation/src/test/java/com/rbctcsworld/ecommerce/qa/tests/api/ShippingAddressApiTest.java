package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AdminClient;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.containsString;

/** Delivery address at checkout (FR-CHK-07..10): required, validated, stored, shown only to who needs it. */
@Tag("api")
@Tag("order")
@Epic("Checkout")
@Feature("Delivery address")
class ShippingAddressApiTest {

    private final CartClient cart = new CartClient();
    private final OrderClient orders = new OrderClient();
    private final AdminClient admin = new AdminClient();
    private Customer customer;

    @BeforeEach
    void customerWithACart() {
        customer = Fixtures.newCustomer();
        cart.add(customer.token(), Fixtures.product("10.00", 5), 1).then().statusCode(201);
    }

    private static Map<String, Object> address(String field, String value) {
        Map<String, Object> a = new HashMap<>(TestData.ADDRESS);
        a.put(field, value);     // HashMap: value may be null
        return a;
    }

    @Test
    @Tag("smoke")
    @DisplayName("No address -> 400 'Shipping address is required', nothing reserved, cart kept")
    void anOrderNeedsAnAddress() {
        orders.placeWithBody(customer.token(), Map.of()).then().statusCode(400)
                .body("message", equalTo("Shipping address is required"));
        cart.get(customer.token()).then().body("items", hasSize(1));
        orders.mine(customer.token()).then().body("", hasSize(0));
    }

    @ParameterizedTest(name = "{0} = ''{1}'' -> 400")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "zip      | 1521",
            "zip      | ABCDE",
            "zip      | 15213-12",
            "state    | ZZ",
            "state    | Pennsylvania",
            "state    | NULL",
            "fullName | ''",
            "line1    | NULL",
            "city     | '   '",
            "phone    | call me"})
    @DisplayName("Each wrong or missing field is named in fieldErrors")
    void invalidFieldsAreNamed(String field, String value) {
        orders.placeWithBody(customer.token(), Map.of("shippingAddress", address(field, value)))
                .then().statusCode(400)
                .body("message", equalTo("Validation failed"))
                .body("fieldErrors", hasKey("shippingAddress." + field));
        cart.get(customer.token()).then().body("items", hasSize(1));
    }

    @Test
    @DisplayName("Boundaries: ZIP+4, lower-case state, no phone, no second line, 80-character name are accepted")
    void acceptedEdgeCases() {
        Map<String, Object> a = new HashMap<>(TestData.ADDRESS);
        a.put("zip", "15213-0001");
        a.put("state", "pa");
        a.put("phone", null);
        a.remove("line2");
        a.put("fullName", "N".repeat(80));
        orders.placeWithBody(customer.token(), Map.of("shippingAddress", a)).then().statusCode(201)
                .body("shippingAddress.state", equalTo("PA"))
                .body("shippingAddress.zip", equalTo("15213-0001"))
                .body("shippingAddress.phone", nullValue())
                .body("shippingAddress.line2", nullValue());
    }

    @Test
    @DisplayName("81-character name -> 400 (one over the limit)")
    void nameOverTheLimit() {
        orders.placeWithBody(customer.token(), Map.of("shippingAddress", address("fullName", "N".repeat(81))))
                .then().statusCode(400).body("fieldErrors", hasKey("shippingAddress.fullName"));
    }

    @Test
    @DisplayName("HTML in a field is stored and returned as plain text, never interpreted")
    void htmlIsJustText() {
        String name = "<img src=x onerror=alert(1)>";
        orders.placeWithBody(customer.token(), Map.of("shippingAddress", address("fullName", name)))
                .then().statusCode(201).body("shippingAddress.fullName", equalTo(name));
    }

    @Test
    @Tag("security")
    @DisplayName("Owner and admin see the address; another customer gets 404; public tracking never shows it")
    void whoSeesTheAddress() {
        long orderId = orders.place(customer.token()).then().statusCode(201)
                .body("shippingAddress.city", equalTo("Pittsburgh")).extract().<Integer>path("id");

        orders.get(customer.token(), orderId).then().statusCode(200).body("shippingAddress.zip", equalTo("15213"));
        orders.get(Fixtures.newCustomer().token(), orderId).then().statusCode(404)
                .body(not(containsString("Lovelace")));
        admin.allOrders(Fixtures.adminToken()).then().statusCode(200)
                .body("findAll { it.id == " + orderId + " }.shippingAddress.fullName", hasItem("Ada Lovelace"));

        orders.pay(customer.token(), orderId, TestData.CARD_OK).then().statusCode(200);
        String tracking = admin.ship(Fixtures.adminToken(), orderId, "UPS").then().statusCode(200)
                .extract().path("trackingNumber");
        orders.publicTracking(tracking).then().statusCode(200)
                .body(allOf(not(containsString("shippingAddress")), not(containsString("Lovelace")),
                        not(containsString("Main St")), not(containsString("15213")), not(containsString("412-555"))));
    }
}
