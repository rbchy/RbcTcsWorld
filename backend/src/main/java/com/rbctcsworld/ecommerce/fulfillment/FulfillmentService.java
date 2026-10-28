package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.fulfillment.FulfillmentDtos.LabelResponse;
import com.rbctcsworld.ecommerce.fulfillment.ShippingProvider.Address;
import com.rbctcsworld.ecommerce.fulfillment.ShippingProvider.Label;
import com.rbctcsworld.ecommerce.fulfillment.ShippingProvider.LabelRequest;
import com.rbctcsworld.ecommerce.order.CustomerOrder;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.order.OrderDtos.TrackingResponse;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import com.rbctcsworld.ecommerce.order.OrderStatus;
import com.rbctcsworld.ecommerce.order.ShippingAddress;
import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Warehouse side (ADMIN): ship a PAID order (with a label from the shipping provider), mark a SHIPPED order
 * delivered - by hand, or when the carrier reports it delivered. Plus the PUBLIC tracking lookup by tracking number.
 */
@Service
@Transactional
public class FulfillmentService {

    static final String NO_LABEL = "This order has no shipping label";
    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    private final OrderRepository orders;
    private final ApplicationEventPublisher events;
    private final ShippingProvider shipping;

    public FulfillmentService(OrderRepository orders, ApplicationEventPublisher events, ShippingProvider shipping) {
        this.orders = orders;
        this.events = events;
        this.shipping = shipping;
    }

    /**
     * 200 SHIPPED | 404 | 409 unless PAID (an unpaid or cancelled order must never leave the warehouse) |
     * 400 no rate or address refused | 503 provider down. The label is bought only after the status check.
     */
    public OrderResponse ship(Long orderId, String carrier) {
        CustomerOrder order = order(orderId);
        order.assertCanMoveTo(OrderStatus.SHIPPED);
        String from = order.getStatus();
        Label label = shipping.buyLabel(new LabelRequest(order.getOrderNumber(), carrier, to(order.getShippingAddress()),
                order.getTotalItems()));
        order.ship(carrier, label.trackingNumber());
        order.recordLabel(label.labelUrl(), label.cost(), label.reference());
        events.publishEvent(AuditEvent.orderStatus(order.getId(), from, order.getStatus()));
        events.publishEvent(OrderNotificationEvent.of(order.getId(), OrderNotificationEvent.Type.ORDER_SHIPPED));
        return OrderResponse.from(order);
    }

    /** 200 DELIVERED | 404 | 409 unless SHIPPED. */
    public OrderResponse deliver(Long orderId) {
        return delivered(order(orderId));
    }

    /** ADMIN: the label to print. 404 unknown order or no label (simulated shipping, or not shipped yet). */
    @Transactional(readOnly = true)
    public LabelResponse label(Long orderId) {
        CustomerOrder o = order(orderId);
        if (o.getLabelUrl() == null) {
            throw new NotFoundException(NO_LABEL);
        }
        return new LabelResponse(o.getCarrier(), o.getTrackingNumber(), o.getLabelUrl(), o.getLabelCost(), shipping.name());
    }

    /**
     * A carrier update (Shippo webhook). The webhook body only says which parcel changed; the status itself is read
     * from the provider, so a forged call cannot deliver an order. Unknown parcels and other statuses change nothing;
     * a parcel already delivered stays as it is (the carrier repeats updates).
     */
    public void onTrackingUpdate(String trackingNumber) {
        CustomerOrder order = orders.findByTrackingNumber(trackingNumber.trim().toUpperCase()).orElse(null);
        if (order == null || !OrderStatus.SHIPPED.equals(order.getStatus())) {
            return;
        }
        String status = shipping.trackingStatus(order.getCarrier(), order.getTrackingNumber());
        if ("DELIVERED".equals(status)) {
            delivered(order);
            log.info("Order {} delivered (reported by {})", order.getOrderNumber(), order.getCarrier());
        }
    }

    /** Public: anyone with the tracking number sees status + timeline, but no personal or price data. */
    @Transactional(readOnly = true)
    public TrackingResponse track(String trackingNumber) {
        return orders.findByTrackingNumber(trackingNumber.trim().toUpperCase())
                .map(TrackingResponse::from)
                .orElseThrow(() -> new NotFoundException("Tracking number not found"));
    }

    private OrderResponse delivered(CustomerOrder order) {
        String from = order.getStatus();
        order.deliver();
        events.publishEvent(AuditEvent.orderStatus(order.getId(), from, order.getStatus()));
        events.publishEvent(OrderNotificationEvent.of(order.getId(), OrderNotificationEvent.Type.ORDER_DELIVERED));
        return OrderResponse.from(order);
    }

    private static Address to(ShippingAddress a) {
        return a == null ? null : new Address(a.fullName(), a.line1(), a.line2(), a.city(), a.state(), a.zip(), a.phone());
    }

    private CustomerOrder order(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }
}
