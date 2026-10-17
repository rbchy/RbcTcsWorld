package com.rbctcsworld.ecommerce.returns;

import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.inventory.InventoryService;
import com.rbctcsworld.ecommerce.inventory.StockMovement;
import com.rbctcsworld.ecommerce.order.CustomerOrder;
import com.rbctcsworld.ecommerce.order.OrderItem;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import com.rbctcsworld.ecommerce.order.OrderService;
import com.rbctcsworld.ecommerce.order.OrderStatus;
import com.rbctcsworld.ecommerce.payment.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Customer: request a return for a DELIVERED order within the return window (one request per order).
 * Admin:    approve (refund per ReturnPolicy + optional restock, order -> RETURNED)
 *           or reject (order goes back to DELIVERED).
 */
@Service
@Transactional
public class ReturnService {

    private final ReturnRequestRepository returns;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final InventoryService inventory;
    private final PaymentService payments;
    private final Clock clock;
    private final int windowDays;
    private final ApplicationEventPublisher events;

    public ReturnService(ReturnRequestRepository returns, OrderRepository orders, OrderService orderService,
                         InventoryService inventory, PaymentService payments, Clock clock,
                         @Value("${app.returns.window-days}") int windowDays, ApplicationEventPublisher events) {
        this.returns = returns;
        this.orders = orders;
        this.orderService = orderService;
        this.inventory = inventory;
        this.payments = payments;
        this.clock = clock;
        this.windowDays = windowDays;
        this.events = events;
    }

    /** 201 | 400 window closed | 404 not mine | 409 not delivered / already requested */
    public ReturnRequest request(String email, Long orderId, String reason, String comment) {
        CustomerOrder order = orderService.owned(email, orderId);
        if (returns.existsByOrderId(order.getId())) {
            throw new ConflictException("A return was already requested for order " + order.getOrderNumber());
        }
        order.assertCanMoveTo(OrderStatus.RETURN_REQUESTED);          // only DELIVERED orders
        LocalDateTime deadline = order.getDeliveredAt().plusDays(windowDays);
        if (LocalDateTime.now(clock).isAfter(deadline)) {
            throw new BusinessRuleException("Return window of " + windowDays + " days has closed for order "
                    + order.getOrderNumber());
        }
        String from = order.getStatus();
        order.transitionTo(OrderStatus.RETURN_REQUESTED, "Return requested: " + reason);
        events.publishEvent(AuditEvent.orderStatus(order.getId(), from, order.getStatus()));
        return returns.save(new ReturnRequest(order.getId(), reason, comment));
    }

    public ReturnRequest approve(Long returnId, String note) {
        ReturnRequest r = pending(returnId);
        CustomerOrder order = orders.findById(r.getOrderId()).orElseThrow();
        boolean restock = ReturnPolicy.restock(r.getReason());
        BigDecimal refund = ReturnPolicy.refundAmount(r.getReason(), order);

        if (restock) {
            order.getItems().stream()
                    .sorted(Comparator.comparing(OrderItem::getProductId))
                    .forEach(i -> inventory.release(i.getProductId(), i.getQuantity(), order.getId(),
                            StockMovement.RETURN_RESTOCK));
        }
        payments.refund(order.getId(), refund);
        String from = order.getStatus();
        order.transitionTo(OrderStatus.RETURNED, "Return approved, refunded " + refund);
        events.publishEvent(AuditEvent.orderStatus(order.getId(), from, order.getStatus()));
        r.approve(refund, restock, note);
        events.publishEvent(new OrderNotificationEvent(order.getId(), OrderNotificationEvent.Type.RETURN_APPROVED, refund));
        return r;
    }

    public ReturnRequest reject(Long returnId, String note) {
        ReturnRequest r = pending(returnId);
        CustomerOrder order = orders.findById(r.getOrderId()).orElseThrow();
        String from = order.getStatus();
        order.transitionTo(OrderStatus.DELIVERED, "Return rejected" + (note == null ? "" : ": " + note));
        events.publishEvent(AuditEvent.orderStatus(order.getId(), from, order.getStatus()));
        r.reject(note);
        events.publishEvent(OrderNotificationEvent.of(order.getId(), OrderNotificationEvent.Type.RETURN_REJECTED));
        return r;
    }

    @Transactional(readOnly = true)
    public List<ReturnRequest> list(String status) {
        return status == null || status.isBlank()
                ? returns.findAllByOrderByIdAsc()
                : returns.findByStatusOrderByIdAsc(status.trim().toUpperCase());
    }

    @Transactional(readOnly = true)
    public ReturnRequest forOrder(String email, Long orderId) {
        CustomerOrder order = orderService.owned(email, orderId);
        return returns.findByOrderId(order.getId())
                .orElseThrow(() -> new NotFoundException("No return for order " + order.getOrderNumber()));
    }

    private ReturnRequest pending(Long id) {
        ReturnRequest r = returns.findById(id).orElseThrow(() -> new NotFoundException("Return not found: " + id));
        if (!ReturnRequest.REQUESTED.equals(r.getStatus())) {
            throw new ConflictException("Return " + id + " is already " + r.getStatus());
        }
        return r;
    }
}
