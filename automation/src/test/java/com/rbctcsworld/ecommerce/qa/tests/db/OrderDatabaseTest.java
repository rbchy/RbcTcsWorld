package com.rbctcsworld.ecommerce.qa.tests.db;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.database.DatabaseUtils;
import com.rbctcsworld.ecommerce.qa.database.OrderQueries;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * API action -> DATABASE verification. Checks that one POST /api/orders wrote exactly the right rows in
 * orders, order_items and stock_movements, updated products.stock, and deleted cart_items.
 * Skipped automatically when PostgreSQL is not reachable.
 */
@Tag("db")
@Epic("Data integrity")
@Feature("Orders and inventory tables")
class OrderDatabaseTest {

    private final CartClient cart = new CartClient();
    private final OrderClient orders = new OrderClient();

    @BeforeAll
    static void databaseReachable() {
        assumeTrue(DatabaseUtils.isAvailable(), "PostgreSQL not reachable - DB validation skipped");
    }

    @Test
    @DisplayName("Place order writes orders + order_items + stock_movements and empties cart_items")
    void placeOrderPersistsEverything() {
        Customer c = Fixtures.newCustomer();
        long a = Fixtures.product("10.00", 5);
        long b = Fixtures.product("2.50", 10);
        cart.add(c.token(), a, 2).then().statusCode(201);
        cart.add(c.token(), b, 4).then().statusCode(201);
        assertEquals(2, OrderQueries.cartLineCount(c.email()));

        int orderId = orders.place(c.token()).then().statusCode(201).extract().path("id");

        Map<String, Object> order = OrderQueries.order(orderId);
        List<Map<String, Object>> items = OrderQueries.orderItems(orderId);
        List<Map<String, Object>> moves = OrderQueries.stockMovementsForOrder(orderId);

        assertNotNull(order, "orders row exists");
        assertAll(
                () -> assertEquals("PLACED", order.get("status")),
                () -> assertEquals(6, ((Number) order.get("total_items")).intValue()),
                () -> assertEquals(0, new BigDecimal("30.00").compareTo((BigDecimal) order.get("subtotal"))),
                () -> assertEquals(2, items.size(), "order_items rows"),
                () -> assertEquals(0, new BigDecimal("20.00").compareTo((BigDecimal) items.get(0).get("line_total"))),
                () -> assertEquals(2, moves.size(), "stock_movements rows"),
                () -> assertEquals(-6, moves.stream().mapToInt(m -> ((Number) m.get("change_qty")).intValue()).sum()),
                () -> assertEquals(3, OrderQueries.productStock(a)),
                () -> assertEquals(6, OrderQueries.productStock(b)),
                () -> assertEquals(0, OrderQueries.cartLineCount(c.email()), "cart emptied"),
                () -> assertEquals("Ada Lovelace", order.get("ship_name"), "delivery address stored on the order"),
                () -> assertEquals("Apt 4", order.get("ship_line2")),
                () -> assertEquals("PA", String.valueOf(order.get("ship_state")).trim()),
                () -> assertEquals("15213", order.get("ship_zip")),
                () -> assertEquals("412-555-0100", order.get("ship_phone")));
    }

    @Test
    @DisplayName("Cancel order: status + cancelled_at set, stock restored, compensating movement written")
    void cancelPersistsEverything() {
        Customer c = Fixtures.newCustomer();
        long p = Fixtures.product("5.00", 4);
        cart.add(c.token(), p, 3).then().statusCode(201);
        int orderId = orders.place(c.token()).then().statusCode(201).extract().path("id");

        orders.cancel(c.token(), orderId).then().statusCode(200);

        Map<String, Object> order = OrderQueries.order(orderId);
        List<Map<String, Object>> moves = OrderQueries.stockMovementsForOrder(orderId);
        assertAll(
                () -> assertEquals("CANCELLED", order.get("status")),
                () -> assertNotNull(order.get("cancelled_at")),
                () -> assertEquals(4, OrderQueries.productStock(p)),
                () -> assertEquals(2, moves.size()),
                () -> assertEquals("ORDER_CANCELLED", moves.get(1).get("reason")),
                () -> assertEquals(4, ((Number) moves.get(1).get("stock_after")).intValue()));
    }

    @Test
    @DisplayName("Inventory invariant: no product anywhere has negative stock")
    void noNegativeStock() {
        Object negatives = DatabaseUtils.scalar("select count(*) from products where stock < 0");
        assertEquals(0, ((Number) negatives).intValue());
    }
}
