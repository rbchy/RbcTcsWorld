package com.rbctcsworld.ecommerce.inventory;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The ONLY place that changes product stock for orders.
 *
 * Overselling protection: the TOTAL is decreased with one conditional SQL statement
 *   UPDATE products SET stock = stock - :qty WHERE id = :id AND active AND stock >= :qty
 * The database executes it atomically, so two customers buying the last unit at the same
 * moment cannot both succeed - one update affects 1 row, the other affects 0 rows.
 * A CHECK (stock >= 0) constraint is a second safety net.
 *
 * Warehouses: that same statement locks the product row, so allocating the units to warehouses
 * happens one order at a time per product. The warehouse rows are locked next (always product
 * first, then its warehouses: one lock order, no deadlocks), each warehouse is decreased with its
 * own conditional UPDATE (quantity >= :qty, plus CHECK quantity >= 0), and every allocation is
 * stored so cancel and return put the units back where they came from.
 * Invariant: products.stock = SUM(warehouse_stock.quantity) for every product.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY) // must run inside the caller's order transaction
public class InventoryService {

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final WarehouseStore warehouses;

    public InventoryService(ProductRepository products, StockMovementRepository movements, WarehouseStore warehouses) {
        this.products = products;
        this.movements = movements;
        this.warehouses = warehouses;
    }

    /** Reserves stock for an order line or throws 409, shipping from the best warehouse (no state preference). */
    public void reserve(Product product, int quantity, Long orderId) {
        reserve(product, quantity, orderId, null);
    }

    /**
     * Reserves stock for an order line or throws 409. Caller's transaction rolls back everything on failure.
     * shipState: the customer's state; a warehouse there is preferred (see {@link WarehouseAllocator}).
     */
    public void reserve(Product product, int quantity, Long orderId, String shipState) {
        int updated = products.decrementStock(product.getId(), quantity);
        if (updated == 0) {
            if (!product.isActive()) {
                throw new ConflictException("Product no longer available: " + product.getSku());
            }
            int available = products.currentStock(product.getId());
            throw new ConflictException("Insufficient stock for " + product.getSku()
                    + ": requested " + quantity + ", available " + available);
        }
        int after = products.currentStock(product.getId());

        List<WarehouseAllocator.Allocation> plan =
                WarehouseAllocator.allocate(warehouses.lockStock(product.getId()), quantity, shipState);
        if (plan.isEmpty()) {
            // the total said yes but the warehouses say no: the invariant is broken - never sell what is not on a shelf
            throw new ConflictException("Insufficient stock in the warehouses for " + product.getSku());
        }
        int still = quantity;                        // units of this line not yet written as a movement
        for (WarehouseAllocator.Allocation a : plan) {
            if (!warehouses.take(a.warehouseId(), product.getId(), a.quantity())) {
                throw new ConflictException("Insufficient stock in the warehouses for " + product.getSku());
            }
            warehouses.allocate(orderId, product.getId(), a.warehouseId(), a.quantity());
            still -= a.quantity();
            movements.save(new StockMovement(product.getId(), orderId, a.warehouseId(), -a.quantity(),
                    StockMovement.ORDER_PLACED, after + still));
        }
    }

    /** Puts stock back when an order is cancelled. */
    public void release(Long productId, int quantity, Long orderId) {
        release(productId, quantity, orderId, StockMovement.ORDER_CANCELLED);
    }

    /**
     * Puts stock back with an explicit reason (ORDER_CANCELLED or RETURN_RESTOCK), into the warehouses
     * the order took it from. Orders from before warehouses existed go back to the default warehouse.
     */
    public void release(Long productId, int quantity, Long orderId, String reason) {
        int before = products.currentStock(productId);
        products.incrementStock(productId, quantity);
        List<WarehouseAllocator.Allocation> taken = warehouses.allocations(orderId, productId);
        if (taken.isEmpty()) {
            taken = List.of(new WarehouseAllocator.Allocation(warehouses.defaultWarehouse().id(), quantity));
        }
        int running = before;
        for (WarehouseAllocator.Allocation a : taken) {
            warehouses.put(a.warehouseId(), productId, a.quantity());
            running += a.quantity();
            movements.save(new StockMovement(productId, orderId, a.warehouseId(), a.quantity(), reason, running));
        }
    }

    // ---------------------------------------------------------------- store admin

    /** A new product's first stock lands in the default warehouse. */
    public void stockNewProduct(long productId, int quantity) {
        if (quantity > 0) warehouses.put(warehouses.defaultWarehouse().id(), productId, quantity);
    }

    /**
     * The admin typed a new total on the product form: the difference goes to (or comes from) the default
     * warehouse. Taking more than that warehouse holds is refused: the admin then corrects the stock per
     * warehouse instead.
     */
    public void changeTotalFromProductForm(long productId, int newTotal) {
        int current = products.currentStock(productId);
        int delta = newTotal - current;
        if (delta == 0) return;
        long home = warehouses.defaultWarehouse().id();
        if (delta > 0) {
            warehouses.put(home, productId, delta);
            products.incrementStock(productId, delta);
        } else {
            if (!warehouses.take(home, productId, -delta)) {
                throw new BusinessRuleException("The stock is spread over several warehouses: lower it per warehouse "
                        + "on the Warehouses page");
            }
            products.incrementStock(productId, delta);
        }
    }
}
