package com.rbctcsworld.ecommerce.returns;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.inventory.InventoryService;
import com.rbctcsworld.ecommerce.inventory.StockMovement;
import com.rbctcsworld.ecommerce.order.CustomerOrder;
import com.rbctcsworld.ecommerce.order.OrderItem;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import com.rbctcsworld.ecommerce.order.OrderService;
import com.rbctcsworld.ecommerce.order.OrderStatus;
import com.rbctcsworld.ecommerce.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {

    private static final String EMAIL = "buyer@test.com";
    /** "now" for every test: 2026-07-31 12:00 */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-31T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock ReturnRequestRepository returns;
    @Mock OrderRepository orders;
    @Mock OrderService orderService;
    @Mock InventoryService inventory;
    @Mock PaymentService payments;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    private ReturnService service;

    @BeforeEach
    void setUp() {
        service = new ReturnService(returns, orders, orderService, inventory, payments, CLOCK, 30, events);
    }

    /** Order total 27.19 = 20.00 + 5.99 shipping + 1.20 tax, delivered at the given time. */
    private static CustomerOrder deliveredOrder(LocalDateTime deliveredAt) {
        CustomerOrder o = new CustomerOrder("ORD-R", 7L);
        ReflectionTestUtils.setField(o, "id", 10L);
        o.addItem(new OrderItem(1L, "SKU-M", "Mouse", new BigDecimal("10.00"), 2));
        o.applyPricing(BigDecimal.ZERO, new BigDecimal("5.99"), new BigDecimal("1.20"), new BigDecimal("27.19"), null);
        ReflectionTestUtils.setField(o, "status", OrderStatus.DELIVERED);
        ReflectionTestUtils.setField(o, "deliveredAt", deliveredAt);
        return o;
    }

    @Test
    void lastMomentOfTheWindowIsAccepted() {
        CustomerOrder o = deliveredOrder(NOW.minusDays(30));            // deadline == now
        when(orderService.owned(EMAIL, 10L)).thenReturn(o);
        when(returns.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnRequest r = service.request(EMAIL, 10L, ReturnPolicy.WRONG_ITEM, null);

        assertThat(r.getStatus()).isEqualTo(ReturnRequest.REQUESTED);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
    }

    @Test
    void oneSecondAfterTheWindowIsRejected() {
        CustomerOrder o = deliveredOrder(NOW.minusDays(30).minusSeconds(1));
        when(orderService.owned(EMAIL, 10L)).thenReturn(o);

        assertThatThrownBy(() -> service.request(EMAIL, 10L, ReturnPolicy.WRONG_ITEM, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("30 days");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void notYetDeliveredCannotBeReturned() {
        CustomerOrder o = deliveredOrder(null);
        ReflectionTestUtils.setField(o, "status", OrderStatus.SHIPPED);
        when(orderService.owned(EMAIL, 10L)).thenReturn(o);

        assertThatThrownBy(() -> service.request(EMAIL, 10L, ReturnPolicy.DAMAGED, null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("SHIPPED");
    }

    @Test
    void onlyOneReturnPerOrder() {
        CustomerOrder o = deliveredOrder(NOW.minusDays(1));
        when(orderService.owned(EMAIL, 10L)).thenReturn(o);
        when(returns.existsByOrderId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.request(EMAIL, 10L, ReturnPolicy.DAMAGED, null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already requested");
    }

    @ParameterizedTest(name = "{0}: refund {1}, restock {2}")
    @CsvSource({
            "DAMAGED,          27.19, false",
            "WRONG_ITEM,       27.19, true",
            "NOT_AS_DESCRIBED, 27.19, true",
            "NO_LONGER_NEEDED, 21.20, true"     // 27.19 - 5.99 shipping
    })
    void approvalFollowsTheDecisionTable(String reason, String refund, boolean restock) {
        CustomerOrder o = deliveredOrder(NOW.minusDays(1));
        ReflectionTestUtils.setField(o, "status", OrderStatus.RETURN_REQUESTED);
        ReturnRequest req = new ReturnRequest(10L, reason, null);
        when(returns.findById(5L)).thenReturn(Optional.of(req));
        when(orders.findById(10L)).thenReturn(Optional.of(o));

        ReturnRequest result = service.approve(5L, "ok");

        assertThat(result.getStatus()).isEqualTo(ReturnRequest.APPROVED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo(refund);
        assertThat(result.isRestocked()).isEqualTo(restock);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.RETURNED);
        verify(payments).refund(10L, new BigDecimal(refund));
        org.mockito.ArgumentCaptor<com.rbctcsworld.ecommerce.notification.OrderNotificationEvent> event = org.mockito.ArgumentCaptor.forClass(com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(10L);
        assertThat(event.getValue().type()).isEqualTo(com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.RETURN_APPROVED);
        assertThat(event.getValue().amount()).isEqualByComparingTo(refund);   // the e-mail states the refund
        if (restock) {
            verify(inventory).release(1L, 2, 10L, StockMovement.RETURN_RESTOCK);
        } else {
            verify(inventory, never()).release(any(), anyInt(), any(), any());
        }
    }

    @Test
    void rejectionPutsOrderBackToDeliveredWithoutRefund() {
        CustomerOrder o = deliveredOrder(NOW.minusDays(1));
        ReflectionTestUtils.setField(o, "status", OrderStatus.RETURN_REQUESTED);
        when(returns.findById(5L)).thenReturn(Optional.of(new ReturnRequest(10L, ReturnPolicy.NO_LONGER_NEEDED, null)));
        when(orders.findById(10L)).thenReturn(Optional.of(o));

        ReturnRequest r = service.reject(5L, "Used item");

        assertThat(r.getStatus()).isEqualTo(ReturnRequest.REJECTED);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(payments, never()).refund(any(), any());
        verify(events).publishEvent(com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.of(10L, com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.RETURN_REJECTED));
    }

    @Test
    void resolvedReturnCannotBeResolvedAgain() {
        ReturnRequest done = new ReturnRequest(10L, ReturnPolicy.DAMAGED, null);
        done.reject("no");
        when(returns.findById(5L)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.approve(5L, null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("already REJECTED");
    }

    @Test
    void rejectionNoteOnTheOrderTimeline() {  // added after PIT: a rejection without a note was never tested
        CustomerOrder o = deliveredOrder(NOW.minusDays(1));
        ReflectionTestUtils.setField(o, "status", OrderStatus.RETURN_REQUESTED);
        when(returns.findById(5L)).thenReturn(Optional.of(new ReturnRequest(10L, ReturnPolicy.NO_LONGER_NEEDED, null)));
        when(orders.findById(10L)).thenReturn(Optional.of(o));

        service.reject(5L, null);

        assertThat(o.getEvents()).last().satisfies(e -> {
            assertThat(e.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(e.getNote()).isEqualTo("Return rejected");
        });
    }
}
