package com.rbctcsworld.ecommerce.inventory;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.Adjust;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.CreateWarehouse;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.StockLine;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.Transfer;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.UpdateWarehouse;
import com.rbctcsworld.ecommerce.inventory.WarehouseDtos.WarehouseView;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)  // the store is asked about several warehouses
class WarehouseServiceTest {

    private static final Warehouse PIT = new Warehouse(1, "PIT", "Pittsburgh DC", "Pittsburgh", "PA", 10, true);
    private static final Warehouse NWK = new Warehouse(3, "NWK", "Newark", "Newark", "NJ", 30, true);
    private static final Warehouse OFF = new Warehouse(4, "OFF", "Closed", "Erie", "PA", 50, false);

    @Mock WarehouseStore store;
    @Mock ProductRepository products;
    @Mock StockMovementRepository movements;
    @Mock ApplicationEventPublisher events;
    @InjectMocks WarehouseService service;

    private Product crate;

    @BeforeEach
    void setUp() {
        crate = new Product("Crate", "SKU-C", "wh", new BigDecimal("4.00"), 5);
        ReflectionTestUtils.setField(crate, "id", 7L);
    }

    /** stockOf(7) as the service builds it after a change. */
    private void stockReadable(int total) {
        when(products.findById(7L)).thenReturn(Optional.of(crate));
        when(store.all()).thenReturn(List.of(PIT, NWK, OFF));
        when(products.currentStock(7L)).thenReturn(total);
    }

    private List<StockMovement> saved(int n) {
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements, times(n)).save(m.capture());
        return m.getAllValues();
    }

    // ---------------------------------------------------------------- warehouses

    @Test
    void theListShowsWhatEachWarehouseHolds() {
        when(store.all()).thenReturn(List.of(PIT, OFF));
        when(store.unitsIn(1L)).thenReturn(40L);
        assertThat(service.all()).containsExactly(new WarehouseView(1, "PIT", "Pittsburgh DC", "Pittsburgh", "PA", 10, true, 40),
                new WarehouseView(4, "OFF", "Closed", "Erie", "PA", 50, false, 0));
    }

    @Test
    void aNewWarehouseGetsANormalisedCodeAndStateAndIsAudited() {
        when(store.codeExists("NY1")).thenReturn(false);
        Warehouse ny = new Warehouse(9, "NY1", "Queens", "New York", "NY", 40, true);
        when(store.insert("NY1", "Queens", "New York", "NY", 40)).thenReturn(9L);
        when(store.find(9L)).thenReturn(Optional.of(ny));

        WarehouseView v = service.create(new CreateWarehouse(" ny1 ", " Queens ", " New York ", "ny", 40));

        assertThat(v.code()).isEqualTo("NY1");
        ArgumentCaptor<AuditEvent> e = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).publishEvent(e.capture());
        assertThat(e.getValue().changes()).extracting(AuditChange::action).containsOnly("WAREHOUSE_CREATED");
        assertThat(e.getValue().changes()).extracting(AuditChange::field)
                .containsExactly("code", "name", "city", "state", "priority", "active");
    }

    @Test
    void aWarehouseCodeIsUnique() {
        when(store.codeExists("PIT")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateWarehouse("pit", "X", "Y", "PA", 1)))
                .isInstanceOf(ConflictException.class).hasMessage("Warehouse code already exists: PIT");
        verify(store, never()).insert(any(), any(), any(), any(), anyInt());
    }

    @Test
    void anEditIsAuditedWithOnlyWhatChanged() {
        Warehouse after = new Warehouse(3, "NWK", "Newark", "Newark", "NJ", 15, true);
        when(store.find(3L)).thenReturn(Optional.of(NWK), Optional.of(after));

        assertThat(service.update(3L, new UpdateWarehouse("Newark", "Newark", "nj", 15, true)).priority()).isEqualTo(15);

        verify(store).update(3L, "Newark", "Newark", "NJ", 15, true);
        verify(events).publishEvent(AuditEvent.of(List.of(
                new AuditChange("WAREHOUSE", 3, "WAREHOUSE_UPDATED", "priority", "30", "15"))));
        verify(store, never()).unitsIn(anyLong());
    }

    @Test
    void aWarehouseIsSwitchedOffOnlyWhenEmptyAndNotTheLastOne() {
        when(store.find(3L)).thenReturn(Optional.of(NWK));
        UpdateWarehouse off = new UpdateWarehouse("Newark", "Newark", "NJ", 30, false);

        when(store.unitsIn(3L)).thenReturn(2L);
        assertThatThrownBy(() -> service.update(3L, off)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("still holds stock");

        when(store.unitsIn(3L)).thenReturn(0L);
        when(store.activeCount()).thenReturn(1);
        assertThatThrownBy(() -> service.update(3L, off)).isInstanceOf(ConflictException.class)
                .hasMessage("At least one warehouse must stay active");

        when(store.activeCount()).thenReturn(2);
        Warehouse closed = new Warehouse(3, "NWK", "Newark", "Newark", "NJ", 30, false);
        when(store.find(3L)).thenReturn(Optional.of(NWK), Optional.of(closed));
        assertThat(service.update(3L, off).active()).isFalse();
        verify(store).update(3L, "Newark", "Newark", "NJ", 30, false);
    }

    @Test
    void switchingOnAgainNeedsNoChecks() {
        Warehouse on = new Warehouse(4, "OFF", "Closed", "Erie", "PA", 50, true);
        when(store.find(4L)).thenReturn(Optional.of(OFF), Optional.of(on));
        assertThat(service.update(4L, new UpdateWarehouse("Closed", "Erie", "PA", 50, true)).active()).isTrue();
        verify(store, never()).activeCount();
    }

    @Test
    void anUnknownWarehouseIs404() {
        when(store.find(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L, new UpdateWarehouse("X", "X", "PA", 1, true)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.adjust(new Adjust(7L, 99L, 1, "RECEIVED"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.transfer(new Transfer(7L, 99L, 1L, 1))).isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- stock

    @Test
    void theStockOfAProductListsEveryWarehouseAlsoTheEmptyOnes() {
        stockReadable(5);
        when(store.quantity(1L, 7L)).thenReturn(5);
        assertThat(service.stockOf(7L).warehouses()).containsExactly(new StockLine(1, "PIT", "Pittsburgh", "PA", true, 5),
                new StockLine(3, "NWK", "Newark", "NJ", true, 0), new StockLine(4, "OFF", "Erie", "PA", false, 0));
        assertThat(service.stockOf(7L).total()).isEqualTo(5);
        when(products.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.stockOf(8L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void receivedGoodsGoIntoTheWarehouseAndTheTotalProductFirst() {
        when(store.find(3L)).thenReturn(Optional.of(NWK));
        when(products.lockById(7L)).thenReturn(Optional.of(crate));
        stockReadable(9);

        assertThat(service.adjust(new Adjust(7L, 3L, 4, "RECEIVED")).total()).isEqualTo(9);

        InOrder order = inOrder(products, store);
        order.verify(products).lockById(7L);
        order.verify(store).lockStock(7L);
        order.verify(store).put(3L, 7L, 4);
        order.verify(products).incrementStock(7L, 4);
        StockMovement m = saved(1).get(0);
        assertThat(m.getChangeQty()).isEqualTo(4);
        assertThat(m.getReason()).isEqualTo("RECEIVED");
        assertThat(m.getWarehouseId()).isEqualTo(3L);
        assertThat(m.getStockAfter()).isEqualTo(9);
        assertThat(m.getOrderId()).isNull();
    }

    @Test
    void damagedUnitsAreTakenOnlyIfTheWarehouseHasThem() {
        when(store.find(1L)).thenReturn(Optional.of(PIT));
        when(products.lockById(7L)).thenReturn(Optional.of(crate));
        when(store.take(1L, 7L, 6)).thenReturn(false);
        when(store.quantity(1L, 7L)).thenReturn(5);
        assertThatThrownBy(() -> service.adjust(new Adjust(7L, 1L, -6, "DAMAGED")))
                .isInstanceOf(ConflictException.class).hasMessage("PIT holds only 5 of this product");
        verify(products, never()).incrementStock(anyLong(), anyInt());

        when(store.take(1L, 7L, 2)).thenReturn(true);
        stockReadable(3);
        service.adjust(new Adjust(7L, 1L, -2, "DAMAGED"));
        verify(products).incrementStock(7L, -2);
    }

    @Test
    void theReasonMustFitTheDirection() {
        assertThatThrownBy(() -> service.adjust(new Adjust(7L, 1L, 0, "COUNT_CORRECTION")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("delta must not be 0");
        assertThatThrownBy(() -> service.adjust(new Adjust(7L, 1L, -1, "RECEIVED")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("RECEIVED adds units");
        assertThatThrownBy(() -> service.adjust(new Adjust(7L, 1L, 1, "DAMAGED")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("DAMAGED removes units");
        verify(store, never()).find(anyLong());
    }

    @Test
    void aSwitchedOffWarehouseReceivesNothingButCanBeEmptied() {
        when(store.find(4L)).thenReturn(Optional.of(OFF));
        assertThatThrownBy(() -> service.adjust(new Adjust(7L, 4L, 1, "COUNT_CORRECTION")))
                .isInstanceOf(ConflictException.class).hasMessage("Warehouse OFF is switched off");

        when(products.lockById(7L)).thenReturn(Optional.of(crate));
        when(store.take(4L, 7L, 1)).thenReturn(true);
        stockReadable(4);
        service.adjust(new Adjust(7L, 4L, -1, "COUNT_CORRECTION"));
        verify(store).take(4L, 7L, 1);
    }

    @Test
    void aTransferMovesUnitsWithoutChangingTheTotal() {
        when(store.find(1L)).thenReturn(Optional.of(PIT));
        when(store.find(3L)).thenReturn(Optional.of(NWK));
        when(products.lockById(7L)).thenReturn(Optional.of(crate));
        when(store.take(1L, 7L, 2)).thenReturn(true);
        stockReadable(5);

        assertThat(service.transfer(new Transfer(7L, 1L, 3L, 2)).total()).isEqualTo(5);

        verify(store).put(3L, 7L, 2);
        verify(products, never()).incrementStock(anyLong(), anyInt());
        List<StockMovement> m = saved(2);
        assertThat(m).extracting(StockMovement::getReason).containsExactly("TRANSFER_OUT", "TRANSFER_IN");
        assertThat(m).extracting(StockMovement::getChangeQty).containsExactly(-2, 2);
        assertThat(m).extracting(StockMovement::getWarehouseId).containsExactly(1L, 3L);
        assertThat(m).extracting(StockMovement::getStockAfter).containsExactly(3, 5);
    }

    @Test
    void aTransferNeedsTwoWarehousesEnoughUnitsAndAnActiveDestination() {
        assertThatThrownBy(() -> service.transfer(new Transfer(7L, 1L, 1L, 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("from and to must be different warehouses");
        when(store.find(1L)).thenReturn(Optional.of(PIT));
        when(store.find(4L)).thenReturn(Optional.of(OFF));
        assertThatThrownBy(() -> service.transfer(new Transfer(7L, 1L, 4L, 1))).isInstanceOf(ConflictException.class);

        when(store.find(3L)).thenReturn(Optional.of(NWK));
        when(products.lockById(7L)).thenReturn(Optional.of(crate));
        when(store.take(1L, 7L, 9)).thenReturn(false);
        when(store.quantity(1L, 7L)).thenReturn(5);
        assertThatThrownBy(() -> service.transfer(new Transfer(7L, 1L, 3L, 9)))
                .isInstanceOf(ConflictException.class).hasMessage("PIT holds only 5 of this product");
        verify(store, never()).put(anyLong(), anyLong(), anyInt());
    }

    @Test
    void aMissingProductIs404BeforeAnythingChanges() {
        when(store.find(1L)).thenReturn(Optional.of(PIT));
        when(products.lockById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.adjust(new Adjust(7L, 1L, 1, "RECEIVED"))).isInstanceOf(NotFoundException.class);
        verify(store, never()).put(anyLong(), anyLong(), anyInt());
    }

    @Test
    void allocationsComeFromTheStore() {
        var a = new WarehouseStore.OrderAllocation(7L, 1L, "PIT", "Pittsburgh", "PA", 2);
        when(store.allocations(5L)).thenReturn(List.of(a));
        assertThat(service.allocations(5L)).containsExactly(a);
    }
}
