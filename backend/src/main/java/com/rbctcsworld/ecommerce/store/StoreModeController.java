package com.rbctcsworld.ecommerce.store;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/store/mode - PUBLIC: {"sales": true|false}, so the storefront shows or hides the shop parts (ADR-0026). */
@RestController
public class StoreModeController {

    public record StoreMode(boolean sales) {
    }

    private final StoreSales sales;

    public StoreModeController(StoreSales sales) {
        this.sales = sales;
    }

    @GetMapping("/api/store/mode")
    public StoreMode mode() {
        return new StoreMode(sales.on());
    }
}
