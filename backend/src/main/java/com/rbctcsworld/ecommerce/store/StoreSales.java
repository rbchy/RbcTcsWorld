package com.rbctcsworld.ecommerce.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Whether the shop sells its own products (ADR-0026). STORE_SALES=off runs the site as a partner (affiliate) site:
 * only products with a partner link are shown to visitors, and cart, checkout, orders, payments, saved cards and
 * guest checkout are closed (409), so nobody can place an order the shop cannot fulfil. on (the default) is the full
 * shop. A typo stops the start: a live site must never guess which of the two it is.
 */
@Component
public class StoreSales {

    private final boolean on;

    public StoreSales(@Value("${app.store.sales:on}") String setting) {
        this.on = parse(setting);
    }

    public static StoreSales of(boolean on) {
        return new StoreSales(on ? "on" : "off");
    }

    static boolean parse(String setting) {
        return switch (setting == null ? "" : setting.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true" -> true;
            case "off", "false" -> false;
            default -> throw new IllegalStateException("STORE_SALES must be 'on' or 'off', not '" + setting + "'");
        };
    }

    public boolean on() {
        return on;
    }
}
