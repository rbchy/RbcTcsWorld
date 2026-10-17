package com.rbctcsworld.ecommerce.inventory;

import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.Adjust;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.CreateWarehouse;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.ProductStock;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.Transfer;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.UpdateWarehouse;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.WarehouseView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Store admin only (/api/admin/** in SecurityConfig).
 * GET  /api/admin/warehouses                      every warehouse with the units it holds
 * POST /api/admin/warehouses                      201 | 400 | 409 code exists
 * PUT  /api/admin/warehouses/{id}                 edit, switch off (only when empty) | 404 | 409
 * GET  /api/admin/products/{id}/stock             the product's stock per warehouse
 * POST /api/admin/stock/adjust                    receive / count correction / damaged
 * POST /api/admin/stock/transfer                  move units between warehouses
 * GET  /api/admin/orders/{id}/allocations         which warehouse ships which units
 */
@RestController
@RequestMapping("/api/admin")
public class WarehouseController {

    private final WarehouseService warehouses;

    public WarehouseController(WarehouseService warehouses) {
        this.warehouses = warehouses;
    }

    @GetMapping("/warehouses")
    public List<WarehouseView> all() {
        return warehouses.all();
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseView create(@Valid @RequestBody CreateWarehouse r) {
        return warehouses.create(r);
    }

    @PutMapping("/warehouses/{id}")
    public WarehouseView update(@PathVariable long id, @Valid @RequestBody UpdateWarehouse r) {
        return warehouses.update(id, r);
    }

    @GetMapping("/products/{id}/stock")
    public ProductStock stock(@PathVariable long id) {
        return warehouses.stockOf(id);
    }

    @PostMapping("/stock/adjust")
    public ProductStock adjust(@Valid @RequestBody Adjust a) {
        return warehouses.adjust(a);
    }

    @PostMapping("/stock/transfer")
    public ProductStock transfer(@Valid @RequestBody Transfer t) {
        return warehouses.transfer(t);
    }

    @GetMapping("/orders/{id}/allocations")
    public List<WarehouseStore.OrderAllocation> allocations(@PathVariable long id) {
        return warehouses.allocations(id);
    }
}
