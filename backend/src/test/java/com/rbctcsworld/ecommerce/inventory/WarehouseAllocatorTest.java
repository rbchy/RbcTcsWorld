package com.rbctcsworld.ecommerce.inventory;

import com.rbctcsworld.ecommerce.inventory.WarehouseAllocator.Allocation;
import com.rbctcsworld.ecommerce.inventory.WarehouseAllocator.Stock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseAllocatorTest {

    private static final Warehouse PIT = new Warehouse(1, "PIT", "Pittsburgh DC", "Pittsburgh", "PA", 10, true);
    private static final Warehouse PHL = new Warehouse(2, "PHL", "Philadelphia", "Philadelphia", "PA", 20, true);
    private static final Warehouse NWK = new Warehouse(3, "NWK", "Newark", "Newark", "NJ", 30, true);
    private static final Warehouse OFF = new Warehouse(4, "OFF", "Closed", "Erie", "PA", 1, false);

    private static Stock s(Warehouse w, int available) {
        return new Stock(w, available);
    }

    @Test
    void oneWarehouseInTheCustomersStateSendsEverything() {
        assertThat(WarehouseAllocator.allocate(List.of(s(PIT, 5), s(NWK, 5)), 3, "nj"))
                .containsExactly(new Allocation(3, 3));
    }

    @Test
    void withoutALocalWarehouseTheLowerPriorityNumberWins() {
        assertThat(WarehouseAllocator.allocate(List.of(s(PHL, 5), s(PIT, 5), s(NWK, 9)), 3, "NY"))
                .containsExactly(new Allocation(1, 3));
        assertThat(WarehouseAllocator.allocate(List.of(s(PHL, 5), s(PIT, 5)), 3, null))
                .containsExactly(new Allocation(1, 3));
    }

    @Test
    void equalPriorityIsDecidedByTheLowerId() {
        Warehouse a = new Warehouse(7, "A", "A", "A", "OH", 50, true);
        Warehouse b = new Warehouse(6, "B", "B", "B", "OH", 50, true);
        assertThat(WarehouseAllocator.allocate(List.of(s(a, 9), s(b, 9)), 1, "OH")).containsExactly(new Allocation(6, 1));
    }

    @Test
    void oneShipmentBeatsALocalSplit() {
        // PA has 2 + 2, Newark alone has 4: one parcel from Newark instead of two from PA
        assertThat(WarehouseAllocator.allocate(List.of(s(PIT, 2), s(PHL, 2), s(NWK, 4)), 4, "PA"))
                .containsExactly(new Allocation(3, 4));
    }

    @Test
    void whenNoWarehouseHasEnoughTheLineIsSplitLocalFirstThenTheFullestWarehouse() {
        assertThat(WarehouseAllocator.allocate(List.of(s(PIT, 1), s(PHL, 2), s(NWK, 5)), 7, "PA"))
                .containsExactly(new Allocation(2, 2), new Allocation(1, 1), new Allocation(3, 4));
        assertThat(WarehouseAllocator.allocate(List.of(s(PIT, 1), s(NWK, 5)), 6, "NJ"))
                .containsExactly(new Allocation(3, 5), new Allocation(1, 1));
    }

    @Test
    void switchedOffAndEmptyWarehousesNeverShip() {
        assertThat(WarehouseAllocator.allocate(List.of(s(OFF, 50), s(PIT, 0), s(PHL, 3)), 3, "PA"))
                .containsExactly(new Allocation(2, 3));
        assertThat(WarehouseAllocator.allocate(List.of(s(OFF, 50), s(PIT, 0)), 1, "PA")).isEmpty();
    }

    @Test
    void tooLittleInAllWarehousesTogetherGivesNoAllocation() {
        assertThat(WarehouseAllocator.allocate(List.of(s(PIT, 2), s(PHL, 2)), 5, "PA")).isEmpty();
        assertThat(WarehouseAllocator.allocate(List.of(), 1, "PA")).isEmpty();
        assertThat(WarehouseAllocator.allocate(List.of(s(PIT, 2), s(PHL, 2)), 4, "PA"))
                .extracting(Allocation::quantity).containsExactly(2, 2);
    }

    @Test
    void theQuantityMustBePositive() {
        assertThatThrownBy(() -> WarehouseAllocator.allocate(List.of(s(PIT, 1)), 0, "PA"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
