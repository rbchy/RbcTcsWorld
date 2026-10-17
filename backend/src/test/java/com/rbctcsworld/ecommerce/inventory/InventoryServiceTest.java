package com.rbctcsworld.ecommerce.inventory;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.inventory.WarehouseAllocator.Allocation;
import com.rbctcsworld.ecommerce.inventory.WarehouseAllocator.Stock;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Warehouse PIT = new Warehouse(1, "PIT", "Pittsburgh DC", "Pittsburgh", "PA", 10, true);
    private static final Warehouse NWK = new Warehouse(3, "NWK", "Newark", "Newark", "NJ", 30, true);

    @Mock ProductRepository products;
    @Mock StockMovementRepository movements;
    @Mock WarehouseStore warehouses;
    @InjectMocks InventoryService inventory;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product("Mouse", "SKU-M", "electronics", new BigDecimal("20.00"), 5);
        ReflectionTestUtils.setField(product, "id", 1L);
    }

    private List<StockMovement> saved(int count) {
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements, times(count)).save(m.capture());
        return m.getAllValues();
    }

    @Test
    void reserveTakesFromTheBestWarehouseRecordsTheAllocationAndANegativeMovement() {
        when(products.decrementStock(1L, 2)).thenReturn(1);
        when(products.currentStock(1L)).thenReturn(3);
        when(warehouses.lockStock(1L)).thenReturn(List.of(new Stock(PIT, 4), new Stock(NWK, 1)));
        when(warehouses.take(1L, 1L, 2)).thenReturn(true);

        inventory.reserve(product, 2, 100L, "PA");

        InOrder order = inOrder(products, warehouses);
        order.verify(products).decrementStock(1L, 2);                 // the product row is locked first ...
        order.verify(warehouses).lockStock(1L);                         // ... then its warehouses
        verify(warehouses).allocate(100L, 1L, 1L, 2);
        StockMovement m = saved(1).get(0);
        assertThat(m.getChangeQty()).isEqualTo(-2);
        assertThat(m.getStockAfter()).isEqualTo(3);
        assertThat(m.getReason()).isEqualTo(StockMovement.ORDER_PLACED);
        assertThat(m.getOrderId()).isEqualTo(100L);
        assertThat(m.getWarehouseId()).isEqualTo(1L);
    }

    @Test
    void aLineBiggerThanOneWarehouseIsSplitAndEachMovementShowsTheRunningTotal() {
        when(products.decrementStock(1L, 5)).thenReturn(1);
        when(products.currentStock(1L)).thenReturn(0);
        when(warehouses.lockStock(1L)).thenReturn(List.of(new Stock(PIT, 3), new Stock(NWK, 2)));
        when(warehouses.take(anyLong(), anyLong(), anyInt())).thenReturn(true);

        inventory.reserve(product, 5, 100L);

        verify(warehouses).allocate(100L, 1L, 1L, 3);
        verify(warehouses).allocate(100L, 1L, 3L, 2);
        List<StockMovement> m = saved(2);
        assertThat(m).extracting(StockMovement::getChangeQty).containsExactly(-3, -2);
        assertThat(m).extracting(StockMovement::getStockAfter).containsExactly(2, 0);
        assertThat(m).extracting(StockMovement::getWarehouseId).containsExactly(1L, 3L);
    }

    @Test
    void reserveFailsWithAvailableQuantityInMessage() {
        when(products.decrementStock(1L, 9)).thenReturn(0);
        when(products.currentStock(1L)).thenReturn(5);

        assertThatThrownBy(() -> inventory.reserve(product, 9, 100L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("requested 9, available 5");
        verify(movements, never()).save(any());
        verify(warehouses, never()).lockStock(anyLong());
    }

    @Test
    void reserveOfDeactivatedProductSaysNoLongerAvailable() {
        product.setActive(false);
        when(products.decrementStock(1L, 1)).thenReturn(0);

        assertThatThrownBy(() -> inventory.reserve(product, 1, 100L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void whenTheWarehousesHoldLessThanTheTotalNothingIsSold() {
        when(products.decrementStock(1L, 3)).thenReturn(1);
        when(products.currentStock(1L)).thenReturn(2);
        when(warehouses.lockStock(1L)).thenReturn(List.of(new Stock(PIT, 2)));

        assertThatThrownBy(() -> inventory.reserve(product, 3, 100L))
                .isInstanceOf(ConflictException.class).hasMessage("Insufficient stock in the warehouses for SKU-M");
        verify(movements, never()).save(any());
    }

    @Test
    void aWarehouseThatRefusesTheTakeStopsTheOrder() {
        when(products.decrementStock(1L, 1)).thenReturn(1);
        when(products.currentStock(1L)).thenReturn(4);
        when(warehouses.lockStock(1L)).thenReturn(List.of(new Stock(PIT, 5)));
        when(warehouses.take(1L, 1L, 1)).thenReturn(false);

        assertThatThrownBy(() -> inventory.reserve(product, 1, 100L)).isInstanceOf(ConflictException.class);
        verify(warehouses, never()).allocate(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void releasePutsUnitsBackWhereTheyCameFrom() {
        when(products.currentStock(1L)).thenReturn(5);
        when(warehouses.allocations(100L, 1L)).thenReturn(List.of(new Allocation(1L, 3), new Allocation(3L, 2)));

        inventory.release(1L, 5, 100L, StockMovement.RETURN_RESTOCK);

        verify(products).incrementStock(1L, 5);
        verify(warehouses).put(1L, 1L, 3);
        verify(warehouses).put(3L, 1L, 2);
        List<StockMovement> m = saved(2);
        assertThat(m).extracting(StockMovement::getChangeQty).containsExactly(3, 2);
        assertThat(m).extracting(StockMovement::getStockAfter).containsExactly(8, 10);
        assertThat(m).extracting(StockMovement::getReason).containsOnly(StockMovement.RETURN_RESTOCK);
        assertThat(m).extracting(StockMovement::getWarehouseId).containsExactly(1L, 3L);
    }

    @Test
    void anOrderFromBeforeWarehousesGoesBackToTheDefaultWarehouse() {
        when(products.currentStock(1L)).thenReturn(5);
        when(warehouses.allocations(100L, 1L)).thenReturn(List.of());
        when(warehouses.defaultWarehouse()).thenReturn(PIT);

        inventory.release(1L, 2, 100L);

        verify(warehouses).put(1L, 1L, 2);
        StockMovement m = saved(1).get(0);
        assertThat(m.getChangeQty()).isEqualTo(2);
        assertThat(m.getReason()).isEqualTo(StockMovement.ORDER_CANCELLED);
        assertThat(m.getStockAfter()).isEqualTo(7);
    }

    @Test
    void aNewProductsFirstStockGoesToTheDefaultWarehouse() {
        when(warehouses.defaultWarehouse()).thenReturn(PIT);
        inventory.stockNewProduct(1L, 7);
        verify(warehouses).put(1L, 1L, 7);

        inventory.stockNewProduct(2L, 0);
        verify(warehouses, never()).put(anyLong(), org.mockito.ArgumentMatchers.eq(2L), anyInt());
    }

    @Test
    void theProductFormChangesTheTotalThroughTheDefaultWarehouse() {
        when(warehouses.defaultWarehouse()).thenReturn(PIT);
        when(products.currentStock(1L)).thenReturn(5);

        inventory.changeTotalFromProductForm(1L, 8);
        verify(warehouses).put(1L, 1L, 3);
        verify(products).incrementStock(1L, 3);

        when(warehouses.take(1L, 1L, 2)).thenReturn(true);
        inventory.changeTotalFromProductForm(1L, 3);
        verify(products).incrementStock(1L, -2);

        inventory.changeTotalFromProductForm(1L, 5);          // unchanged: nothing happens
        verify(products, times(2)).incrementStock(anyLong(), anyInt());
    }

    @Test
    void theProductFormCannotTakeMoreThanTheDefaultWarehouseHolds() {
        when(warehouses.defaultWarehouse()).thenReturn(PIT);
        when(products.currentStock(1L)).thenReturn(5);
        when(warehouses.take(1L, 1L, 5)).thenReturn(false);

        assertThatThrownBy(() -> inventory.changeTotalFromProductForm(1L, 0))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Warehouses page");
        verify(products, never()).incrementStock(anyLong(), anyInt());
    }
}
