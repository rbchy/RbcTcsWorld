package com.rbctcsworld.ecommerce.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which warehouses ship an order line. Pure logic, no database: easy to test and to explain.
 *
 * Rules, in this order:
 *  1. Only active warehouses that hold the product count.
 *  2. One shipment beats two: if one warehouse can send the whole quantity, it does. Among those, a
 *     warehouse in the customer's state wins, then the lower priority number, then the lower id.
 *  3. Otherwise the line is split: same state first, then the warehouse holding the most, then priority,
 *     so the fewest warehouses are used.
 */
public final class WarehouseAllocator {

    private WarehouseAllocator() {
    }

    /** What one warehouse holds of the product. */
    public record Stock(Warehouse warehouse, int available) {
    }

    /** Units of the line taken from one warehouse. */
    public record Allocation(long warehouseId, int quantity) {
    }

    /** The allocations (quantities add up to {@code quantity}); empty when the warehouses together hold too little. */
    public static List<Allocation> allocate(List<Stock> stocks, int quantity, String shipState) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        String state = shipState == null ? "" : shipState.trim().toUpperCase();
        Comparator<Stock> sameStateFirst = Comparator.comparing(s -> !s.warehouse().state().equalsIgnoreCase(state));
        Comparator<Stock> byPriority = Comparator.<Stock>comparingInt(s -> s.warehouse().priority())
                .thenComparingLong(s -> s.warehouse().id());
        List<Stock> usable = stocks.stream().filter(s -> s.warehouse().active() && s.available() > 0).toList();

        // rule 2: one warehouse sends everything
        var whole = usable.stream().filter(s -> s.available() >= quantity).min(sameStateFirst.thenComparing(byPriority));
        if (whole.isPresent()) return List.of(new Allocation(whole.get().warehouse().id(), quantity));

        // rule 3: split, using as few warehouses as possible
        List<Stock> order = new ArrayList<>(usable);
        order.sort(sameStateFirst.thenComparing(Comparator.comparingInt(Stock::available).reversed()).thenComparing(byPriority));
        List<Allocation> result = new ArrayList<>();
        int left = quantity;
        for (Stock s : order) {
            if (left == 0) break;
            int take = Math.min(left, s.available());
            result.add(new Allocation(s.warehouse().id(), take));
            left -= take;
        }
        return left == 0 ? result : List.of();
    }
}
