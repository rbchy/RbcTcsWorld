package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.order.CustomerOrder;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import com.rbctcsworld.ecommerce.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentServiceTest {

    @Mock OrderRepository orders;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    FulfillmentService service;

    @org.junit.jupiter.api.BeforeEach
    void simulatedShipping() {
        service = new FulfillmentService(orders, events, new SimulatedShippingProvider(orders));
    }

    private CustomerOrder order(String status) {
        CustomerOrder o = new CustomerOrder("ORD-F", 7L);
        ReflectionTestUtils.setField(o, "id", 3L);
        ReflectionTestUtils.setField(o, "status", status);
        when(orders.findById(3L)).thenReturn(Optional.of(o));
        return o;
    }

    @ParameterizedTest(name = "carrier {0}")
    @ValueSource(strings = {"UPS", "FEDEX", "USPS"})
    void shipPaidOrderGeneratesTrackingNumber(String carrier) {
        order(OrderStatus.PAID);
        OrderResponse r = service.ship(3L, carrier);

        assertThat(r.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(r.carrier()).isEqualTo(carrier);
        assertThat(r.trackingNumber()).matches(carrier + "-\\d{12}");
        assertThat(r.shippedAt()).isNotNull();
        org.mockito.Mockito.verify(events).publishEvent(com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.of(3L, com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.ORDER_SHIPPED));
        org.mockito.Mockito.verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.orderStatus(3L, "PAID", "SHIPPED"));
    }

    @org.junit.jupiter.api.Test
    void deliveringTellsTheCustomer() {
        order(OrderStatus.SHIPPED);
        assertThat(service.deliver(3L).status()).isEqualTo(OrderStatus.DELIVERED);
        org.mockito.Mockito.verify(events).publishEvent(com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.of(3L, com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.ORDER_DELIVERED));
        org.mockito.Mockito.verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.orderStatus(3L, "SHIPPED", "DELIVERED"));
    }

    @org.junit.jupiter.api.Test
    void nothingIsAnnouncedWhenTheMoveIsRefused() {
        order(OrderStatus.PLACED);
        assertThatThrownBy(() -> service.ship(3L, "UPS")).isInstanceOf(ConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(events);
        // refused before any work: no tracking number is even generated
        org.mockito.Mockito.verify(orders, org.mockito.Mockito.never()).existsByTrackingNumber(org.mockito.ArgumentMatchers.anyString());
    }

    @ParameterizedTest(name = "cannot ship a {0} order")
    @ValueSource(strings = {"PLACED", "CANCELLED", "SHIPPED", "DELIVERED"})
    void onlyPaidOrdersShip(String status) {
        order(status);
        assertThatThrownBy(() -> service.ship(3L, "UPS")).isInstanceOf(ConflictException.class);
    }

    @Test
    void deliverRequiresShipped() {
        order(OrderStatus.PAID);
        assertThatThrownBy(() -> service.deliver(3L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownTrackingNumberIs404() {
        when(orders.findByTrackingNumber("UPS-000000000000")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.track(" ups-000000000000 ")).isInstanceOf(NotFoundException.class);
    }

    // ---- labels from a shipping provider (store phase 3: Shippo) ----

    private CustomerOrder paidWithAddress() {
        CustomerOrder o = order(OrderStatus.PAID);
        o.shipTo(com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);
        ReflectionTestUtils.setField(o, "totalItems", 3);
        return o;
    }

    @Test
    void theProvidersLabelIsBoughtForTheOrderAndKept() {
        CustomerOrder o = paidWithAddress();
        ShippingProvider shippo = org.mockito.Mockito.mock(ShippingProvider.class);
        when(shippo.buyLabel(org.mockito.ArgumentMatchers.any())).thenReturn(new ShippingProvider.Label(
                "9400111899223197428490", "https://shippo.example/label.pdf", new java.math.BigDecimal("7.58"), "txn_1"));
        FulfillmentService withShippo = new FulfillmentService(orders, events, shippo);

        OrderResponse r = withShippo.ship(3L, "USPS");

        assertThat(r.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(r.trackingNumber()).isEqualTo("9400111899223197428490");
        assertThat(o.getLabelUrl()).isEqualTo("https://shippo.example/label.pdf");
        assertThat(o.getLabelCost()).isEqualByComparingTo("7.58");
        assertThat(o.getLabelRef()).isEqualTo("txn_1");
        org.mockito.ArgumentCaptor<ShippingProvider.LabelRequest> asked = org.mockito.ArgumentCaptor.forClass(ShippingProvider.LabelRequest.class);
        org.mockito.Mockito.verify(shippo).buyLabel(asked.capture());
        assertThat(asked.getValue()).isEqualTo(new ShippingProvider.LabelRequest("ORD-F", "USPS",
                new ShippingProvider.Address("Ada Lovelace", "12 Main St", "Apt 4", "Pittsburgh", "PA", "15213", "412-555-0100"), 3));
    }

    @Test
    void whenNoLabelCanBeBoughtTheOrderStaysPaidAndNothingIsAnnounced() {
        CustomerOrder o = paidWithAddress();
        ShippingProvider shippo = org.mockito.Mockito.mock(ShippingProvider.class);
        when(shippo.buyLabel(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException("down"));

        assertThatThrownBy(() -> new FulfillmentService(orders, events, shippo).ship(3L, "UPS"))
                .isInstanceOf(com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(o.getTrackingNumber()).isNull();
        org.mockito.Mockito.verifyNoInteractions(events);
    }

    @Test
    void anOrderWithoutAnAddressIsSentWithoutOne() {
        order(OrderStatus.PAID);                                  // orders from before addresses were required
        ShippingProvider shippo = org.mockito.Mockito.mock(ShippingProvider.class);
        when(shippo.buyLabel(org.mockito.ArgumentMatchers.any())).thenReturn(new ShippingProvider.Label("T1", null, null, null));
        new FulfillmentService(orders, events, shippo).ship(3L, "UPS");
        org.mockito.Mockito.verify(shippo).buyLabel(new ShippingProvider.LabelRequest("ORD-F", "UPS", null, 0));
    }

    @Test
    void theLabelToPrintIsShownOnlyWhenOneWasBought() {
        CustomerOrder o = order(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.label(3L)).isInstanceOf(NotFoundException.class).hasMessage(FulfillmentService.NO_LABEL);

        ReflectionTestUtils.setField(o, "carrier", "USPS");
        ReflectionTestUtils.setField(o, "trackingNumber", "9400111899223197428490");
        o.recordLabel("https://shippo.example/label.pdf", new java.math.BigDecimal("7.58"), "txn_1");
        assertThat(service.label(3L)).isEqualTo(new FulfillmentDtos.LabelResponse("USPS", "9400111899223197428490",
                "https://shippo.example/label.pdf", new java.math.BigDecimal("7.58"), "simulated"));
    }

    @Test
    void simulatedShippingKeepsNoLabel() {
        CustomerOrder o = order(OrderStatus.PAID);
        service.ship(3L, "UPS");
        assertThat(o.getLabelUrl()).isNull();
        assertThat(o.getLabelCost()).isNull();
        assertThat(o.getLabelRef()).isNull();
    }

    // ---- carrier updates (Shippo webhook) ----

    private CustomerOrder shippedWith(ShippingProvider provider, String status) {
        CustomerOrder o = new CustomerOrder("ORD-T", 7L);
        ReflectionTestUtils.setField(o, "id", 4L);
        ReflectionTestUtils.setField(o, "status", status);
        ReflectionTestUtils.setField(o, "carrier", "USPS");
        ReflectionTestUtils.setField(o, "trackingNumber", "9400111899223197428490");
        when(orders.findByTrackingNumber("9400111899223197428490")).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void aParcelTheCarrierReportsDeliveredDeliversTheOrder() {
        ShippingProvider shippo = org.mockito.Mockito.mock(ShippingProvider.class);
        CustomerOrder o = shippedWith(shippo, OrderStatus.SHIPPED);
        when(shippo.trackingStatus("USPS", "9400111899223197428490")).thenReturn("DELIVERED");

        new FulfillmentService(orders, events, shippo).onTrackingUpdate(" 9400111899223197428490 ");

        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        org.mockito.Mockito.verify(events).publishEvent(com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.of(4L,
                com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.ORDER_DELIVERED));
        org.mockito.Mockito.verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.orderStatus(4L, "SHIPPED", "DELIVERED"));
    }

    @ParameterizedTest(name = "carrier says {0}")
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"TRANSIT", "PRE_TRANSIT", "FAILURE", "delivered"})
    void anyOtherCarrierStatusChangesNothing(String status) {
        ShippingProvider shippo = org.mockito.Mockito.mock(ShippingProvider.class);
        CustomerOrder o = shippedWith(shippo, OrderStatus.SHIPPED);
        when(shippo.trackingStatus("USPS", "9400111899223197428490")).thenReturn(status);
        new FulfillmentService(orders, events, shippo).onTrackingUpdate("9400111899223197428490");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        org.mockito.Mockito.verifyNoInteractions(events);
    }

    @ParameterizedTest(name = "an order that is {0}")
    @ValueSource(strings = {"DELIVERED", "PAID", "CANCELLED"})
    void onlyAShippedOrderIsCheckedWithTheCarrier(String status) {
        ShippingProvider shippo = org.mockito.Mockito.mock(ShippingProvider.class);
        shippedWith(shippo, status);
        new FulfillmentService(orders, events, shippo).onTrackingUpdate("9400111899223197428490");
        org.mockito.Mockito.verifyNoInteractions(shippo, events);
    }

    @Test
    void anUnknownParcelChangesNothing() {
        ShippingProvider shippo = org.mockito.Mockito.mock(ShippingProvider.class);
        when(orders.findByTrackingNumber("NOPE")).thenReturn(Optional.empty());
        new FulfillmentService(orders, events, shippo).onTrackingUpdate("nope");
        org.mockito.Mockito.verifyNoInteractions(shippo, events);
    }

    @Test
    void theSimulatedProviderMakesUpAUniqueNumberAndKnowsNoStatus() {
        when(orders.existsByTrackingNumber(org.mockito.ArgumentMatchers.anyString())).thenReturn(true, true, false);
        SimulatedShippingProvider simulated = new SimulatedShippingProvider(orders);
        ShippingProvider.Label l = simulated.buyLabel(new ShippingProvider.LabelRequest("ORD-1", "FEDEX", null, 1));
        assertThat(l.trackingNumber()).matches("FEDEX-\\d{12}");
        assertThat(l.labelUrl()).isNull();
        org.mockito.Mockito.verify(orders, org.mockito.Mockito.times(3)).existsByTrackingNumber(org.mockito.ArgumentMatchers.anyString());
        assertThat(simulated.name()).isEqualTo("simulated");
        assertThat(simulated.trackingStatus("UPS", "UPS-1")).isNull();
    }
}
