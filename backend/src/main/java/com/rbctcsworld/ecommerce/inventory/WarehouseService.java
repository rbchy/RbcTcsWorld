package com.rbctcsworld.ecommerce.inventory;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.Adjust;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.CreateWarehouse;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.ProductStock;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.StockLine;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.Transfer;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.UpdateWarehouse;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.WarehouseView;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Store admin: warehouses, and stock per warehouse (receive goods, correct a count, write off damage,
 * move stock between warehouses). Every change keeps products.stock = SUM(warehouse_stock) and writes
 * stock movements; warehouse changes go to the audit trail.
 *
 * Lock order is the same as for orders: the product row first, then its warehouse rows.
 */
@Service
@Transactional
public class WarehouseService {

    private final WarehouseStore store;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final ApplicationEventPublisher events;

    public WarehouseService(WarehouseStore store, ProductRepository products, StockMovementRepository movements,
                            ApplicationEventPublisher events) {
        this.store = store;
        this.products = products;
        this.movements = movements;
        this.events = events;
    }

    // ---------------------------------------------------------------- warehouses

    @Transactional(readOnly = true)
    public List<WarehouseView> all() {
        return store.all().stream().map(this::view).toList();
    }

    public WarehouseView create(CreateWarehouse r) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        if (store.codeExists(code)) throw new ConflictException("Warehouse code already exists: " + code);
        long id = store.insert(code, r.name().trim(), r.city().trim(), r.state().trim().toUpperCase(Locale.ROOT), r.priority());
        Warehouse w = store.find(id).orElseThrow();
        events.publishEvent(AuditEvent.of(AuditChange.diff(AuditChange.WAREHOUSE, id, "WAREHOUSE_CREATED", null, audited(w))));
        return view(w);
    }

    /**
     * Edits a warehouse. Switching one off is only allowed when it is empty (move its stock first) and
     * another warehouse stays active (new stock needs a home).
     */
    public WarehouseView update(long id, UpdateWarehouse r) {
        Warehouse w = store.find(id).orElseThrow(() -> new NotFoundException("Warehouse not found: " + id));
        if (w.active() && !r.active()) {
            if (store.unitsIn(id) > 0) {
                throw new ConflictException("Warehouse " + w.code() + " still holds stock: move it to another warehouse first");
            }
            if (store.activeCount() <= 1) throw new ConflictException("At least one warehouse must stay active");
        }
        store.update(id, r.name().trim(), r.city().trim(), r.state().trim().toUpperCase(Locale.ROOT), r.priority(), r.active());
        Warehouse after = store.find(id).orElseThrow();
        events.publishEvent(AuditEvent.of(AuditChange.diff(AuditChange.WAREHOUSE, id, "WAREHOUSE_UPDATED", audited(w),
                audited(after))));
        return view(after);
    }

    // ---------------------------------------------------------------- stock per warehouse

    /** The product's stock in every warehouse (also the empty ones), total first. */
    @Transactional(readOnly = true)
    public ProductStock stockOf(long productId) {
        Product p = products.findById(productId).orElseThrow(() -> new NotFoundException("Product not found: " + productId));
        List<StockLine> lines = store.all().stream().map(w -> new StockLine(w.id(), w.code(), w.city(), w.state(),
                w.active(), store.quantity(w.id(), productId))).toList();
        return new ProductStock(productId, p.getSku(), p.getName(), products.currentStock(productId), lines);
    }

    /** Received goods (+), a count correction (+/-) or damaged units written off (-), in one warehouse. */
    public ProductStock adjust(Adjust a) {
        if (a.delta() == 0) throw new BusinessRuleException("delta must not be 0");
        if (a.reason().equals(StockMovement.RECEIVED) && a.delta() < 0) throw new BusinessRuleException("RECEIVED adds units");
        if (a.reason().equals(StockMovement.DAMAGED) && a.delta() > 0) throw new BusinessRuleException("DAMAGED removes units");
        Warehouse w = activeWarehouse(a.warehouseId(), a.delta() > 0);
        lockProduct(a.productId());
        store.lockStock(a.productId());
        if (a.delta() > 0) {
            store.put(w.id(), a.productId(), a.delta());
        } else if (!store.take(w.id(), a.productId(), -a.delta())) {
            throw new ConflictException(w.code() + " holds only " + store.quantity(w.id(), a.productId()) + " of this product");
        }
        products.incrementStock(a.productId(), a.delta());
        movements.save(new StockMovement(a.productId(), null, w.id(), a.delta(), a.reason(),
                products.currentStock(a.productId())));
        return stockOf(a.productId());
    }

    /** Moves units from one warehouse to another; the total does not change. */
    public ProductStock transfer(Transfer t) {
        if (t.fromWarehouseId().equals(t.toWarehouseId())) throw new BusinessRuleException("from and to must be different warehouses");
        Warehouse from = store.find(t.fromWarehouseId())
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + t.fromWarehouseId()));
        Warehouse to = activeWarehouse(t.toWarehouseId(), true);
        lockProduct(t.productId());
        store.lockStock(t.productId());
        if (!store.take(from.id(), t.productId(), t.quantity())) {
            throw new ConflictException(from.code() + " holds only " + store.quantity(from.id(), t.productId()) + " of this product");
        }
        store.put(to.id(), t.productId(), t.quantity());
        int total = products.currentStock(t.productId());
        movements.save(new StockMovement(t.productId(), null, from.id(), -t.quantity(), StockMovement.TRANSFER_OUT, total - t.quantity()));
        movements.save(new StockMovement(t.productId(), null, to.id(), t.quantity(), StockMovement.TRANSFER_IN, total));
        return stockOf(t.productId());
    }

    @Transactional(readOnly = true)
    public List<WarehouseStore.OrderAllocation> allocations(long orderId) {
        return store.allocations(orderId);
    }

    // ---------------------------------------------------------------- helpers

    /** Units may only go INTO an active warehouse; taking out of a switched-off one (emptying it) is fine. */
    private Warehouse activeWarehouse(long id, boolean receiving) {
        Warehouse w = store.find(id).orElseThrow(() -> new NotFoundException("Warehouse not found: " + id));
        if (receiving && !w.active()) throw new ConflictException("Warehouse " + w.code() + " is switched off");
        return w;
    }

    private void lockProduct(long productId) {
        products.lockById(productId).orElseThrow(() -> new NotFoundException("Product not found: " + productId));
    }

    private WarehouseView view(Warehouse w) {
        return new WarehouseView(w.id(), w.code(), w.name(), w.city(), w.state(), w.priority(), w.active(), store.unitsIn(w.id()));
    }

    static java.util.Map<String, Object> audited(Warehouse w) {
        return AuditChange.fields("code", w.code(), "name", w.name(), "city", w.city(), "state", w.state(),
                "priority", w.priority(), "active", w.active());
    }
}
