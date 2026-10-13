package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.order.CustomerOrder;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.order.OrderDtos.TrackingResponse;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import com.rbctcsworld.ecommerce.order.OrderStatus;
import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * Warehouse side (ADMIN): ship a PAID order, mark a SHIPPED order delivered.
 * Plus the PUBLIC tracking lookup by tracking number.
 */
@Service
@Transactional
public class FulfillmentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final ApplicationEventPublisher events;

    public FulfillmentService(OrderRepository orders, ApplicationEventPublisher events) {
        this.orders = orders;
        this.events = events;
    }

    /** 200 SHIPPED | 404 | 409 unless PAID (an unpaid or cancelled order must never leave the warehouse). */
    public OrderResponse ship(Long orderId, String carrier) {
        CustomerOrder order = order(orderId);
        order.assertCanMoveTo(OrderStatus.SHIPPED);
        order.ship(carrier, newTrackingNumber(carrier));
        events.publishEvent(OrderNotificationEvent.of(order.getId(), OrderNotificationEvent.Type.ORDER_SHIPPED));
        return OrderResponse.from(order);
    }

    /** 200 DELIVERED | 404 | 409 unless SHIPPED. */
    public OrderResponse deliver(Long orderId) {
        CustomerOrder order = order(orderId);
        order.deliver();
        events.publishEvent(OrderNotificationEvent.of(order.getId(), OrderNotificationEvent.Type.ORDER_DELIVERED));
        return OrderResponse.from(order);
    }

    /** Public: anyone with the tracking number sees status + timeline, but no personal or price data. */
    @Transactional(readOnly = true)
    public TrackingResponse track(String trackingNumber) {
        return orders.findByTrackingNumber(trackingNumber.trim().toUpperCase())
                .map(TrackingResponse::from)
                .orElseThrow(() -> new NotFoundException("Tracking number not found"));
    }

    private CustomerOrder order(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    /** e.g. UPS-482910384756 (carrier + 12 random digits, unique). */
    String newTrackingNumber(String carrier) {
        String tn;
        do {
            StringBuilder sb = new StringBuilder(carrier).append('-');
            for (int i = 0; i < 12; i++) sb.append(RANDOM.nextInt(10));
            tn = sb.toString();
        } while (orders.existsByTrackingNumber(tn));
        return tn;
    }
}
