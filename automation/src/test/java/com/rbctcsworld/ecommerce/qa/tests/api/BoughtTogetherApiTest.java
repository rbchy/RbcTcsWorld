package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.api.ProductClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

/** "Customers who bought this also bought", built from real orders: REQ-CAT-13. */
@Tag("api")
@Tag("catalog")
@Epic("Catalog")
@Feature("Bought together")
class BoughtTogetherApiTest {

    private final ProductClient products = new ProductClient();
    private final CartClient cart = new CartClient();
    private final OrderClient orders = new OrderClient();

    /** A new customer buys these products in one order. */
    private void buy(long... ids) {
        Customer c = Fixtures.newCustomer();
        for (long id : ids) cart.add(c.token(), id, 1).then().statusCode(201);
        orders.place(c.token()).then().statusCode(201);
    }

    @Test
    @Tag("smoke")
    @DisplayName("The product bought most often with this one comes first")
    void rankedByHowOftenTheyShareAnOrder() {
        long a = Fixtures.namedProduct("10.00", 50).id();
        var often = Fixtures.namedProduct("11.00", 50);
        var rare = Fixtures.namedProduct("12.00", 50);
        buy(a, often.id());
        buy(a, often.id(), rare.id());
        buy(a, often.id());
        buy(often.id(), rare.id());                                    // no "a": does not make "rare" more popular for a

        products.boughtTogether(a, null).then().statusCode(200)
                .body("id", contains((int) often.id(), (int) rare.id()))
                .body("name", contains(often.name(), rare.name()));
        products.boughtTogether(a, 1).then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    @DisplayName("A product nobody bought has no recommendations; a bad limit is 400; an unknown product is 404")
    void edges() {
        long fresh = Fixtures.namedProduct("10.00", 5).id();
        products.boughtTogether(fresh, null).then().statusCode(200).body("$", empty());
        products.boughtTogether(fresh, 0).then().statusCode(400);
        products.boughtTogether(fresh, 13).then().statusCode(400);
        products.boughtTogether(999_999_999L, null).then().statusCode(404);
    }
}
