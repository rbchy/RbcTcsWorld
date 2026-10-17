package com.rbctcsworld.ecommerce.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class WarehouseDtos {

    private WarehouseDtos() {
    }

    public record CreateWarehouse(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,10}", message = "2 to 10 letters or digits") String code,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 60) String city,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "two-letter state code") String state,
            @NotNull @Min(1) @Max(999) Integer priority) {
    }

    public record UpdateWarehouse(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 60) String city,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "two-letter state code") String state,
            @NotNull @Min(1) @Max(999) Integer priority,
            @NotNull Boolean active) {
    }

    /** A warehouse with what it holds. */
    public record WarehouseView(long id, String code, String name, String city, String state, int priority,
                                boolean active, long units) {
    }

    /** Stock correction in one warehouse. delta > 0: RECEIVED or COUNT_CORRECTION; delta < 0: DAMAGED or COUNT_CORRECTION. */
    public record Adjust(@NotNull Long productId, @NotNull Long warehouseId, @NotNull Integer delta,
                         @NotBlank @Pattern(regexp = "RECEIVED|COUNT_CORRECTION|DAMAGED",
                                 message = "must be RECEIVED, COUNT_CORRECTION or DAMAGED") String reason) {
    }

    public record Transfer(@NotNull Long productId, @NotNull Long fromWarehouseId, @NotNull Long toWarehouseId,
                           @NotNull @Positive @Max(100000) Integer quantity) {
    }

    /** One warehouse's quantity of a product (0 when it holds none). */
    public record StockLine(long warehouseId, String code, String city, String state, boolean active, int quantity) {
    }

    /** A product's stock per warehouse; total = products.stock = the sum of the lines. */
    public record ProductStock(long productId, String sku, String name, int total, java.util.List<StockLine> warehouses) {
    }
}
