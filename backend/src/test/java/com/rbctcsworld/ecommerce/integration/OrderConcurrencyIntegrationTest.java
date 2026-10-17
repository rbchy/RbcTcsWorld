package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.cart.CartItem;
import com.rbctcsworld.ecommerce.cart.CartItemRepository;
import com.rbctcsworld.ecommerce.order.OrderService;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Overselling test: 10 customers try to buy the last 3 units at the same moment.
 *
 * Invariants checked here (on H2): never more successful orders than stock, stock never negative,
 * stock + reserved units always equals the starting stock. H2's row locking is not identical to
 * PostgreSQL's, so the EXACT "3 of 10 succeed" assertion lives in the automation suite
 * (InventoryConcurrencyTest), which runs against the real PostgreSQL database.
 */
@SpringBootTest
@AutoConfigureMockMvc // same Spring context as the other integration tests -> started only once
@ActiveProfiles("test")
class OrderConcurrencyIntegrationTest {

    private static final int CUSTOMERS = 10;
    private static final int STOCK = 3;

    @Autowired OrderService orderService;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired CartItemRepository cartItems;
    @Autowired JdbcTemplate jdbc;

    @Test
    void lastUnitsCannotBeOversold() throws Exception {
        Product limited = products.save(new Product("Flash sale item",
                "RACE-" + UUID.randomUUID().toString().substring(0, 8), "test", new BigDecimal("1.00"), STOCK));

        List<String> emails = new ArrayList<>();
        for (int i = 0; i < CUSTOMERS; i++) {
            String email = "race-" + i + "-" + UUID.randomUUID() + "@test.com";
            AppUser u = users.save(new AppUser(email, "not-used"));
            cartItems.save(new CartItem(u.getId(), limited, 1));
            emails.add(email);
        }

        ExecutorService pool = Executors.newFixedThreadPool(CUSTOMERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (String email : emails) {
            results.add(pool.submit(() -> {
                start.await();                 // all threads fire together
                try {
                    orderService.placeOrder(email, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);
                    return true;
                } catch (RuntimeException rejected) {
                    return false;
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        long successes = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) successes++;
        }

        Integer finalStock = jdbc.queryForObject("select stock from products where id = ?", Integer.class, limited.getId());
        Integer reserved = jdbc.queryForObject(
                "select coalesce(-sum(change_qty), 0) from stock_movements where product_id = ?", Integer.class, limited.getId());

        assertThat(successes).as("at least one buyer wins").isGreaterThanOrEqualTo(1);
        assertThat(successes).as("never more orders than stock (no overselling)").isLessThanOrEqualTo(STOCK);
        assertThat(finalStock).as("stock never negative").isGreaterThanOrEqualTo(0);
        assertThat(finalStock).as("stock = start - sold").isEqualTo(STOCK - (int) successes);
        assertThat(reserved).as("stock movements match sold units").isEqualTo((int) successes);
    }
}
