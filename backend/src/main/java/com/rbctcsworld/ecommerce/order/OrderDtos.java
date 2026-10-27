package com.rbctcsworld.ecommerce.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * Body of POST /api/orders and POST /api/checkout/quote. The quote ignores the address; placing an order
     * needs one (400 "Shipping address is required" otherwise).
     */
    public record CheckoutRequest(@Size(max = 40) String couponCode, @Valid ShippingAddress shippingAddress) {
    }

    public record OrderLine(Long productId, String sku, String name, BigDecimal unitPrice,
                            int quantity, BigDecimal lineTotal) {
    }

    /** "Review your order" page: what the customer WOULD pay. Nothing is saved or reserved. */
    public record QuoteResponse(List<OrderLine> items, int totalQuantity, BigDecimal subtotal, String couponCode,
                                BigDecimal discount, BigDecimal shippingFee, BigDecimal tax, BigDecimal total,
                                boolean taxEstimated) {
    }

    public record OrderResponse(Long id, String orderNumber, Long customerId, String status,
                                List<OrderLine> items, int totalQuantity, BigDecimal subtotal,
                                String couponCode, BigDecimal discount, BigDecimal shippingFee,
                                BigDecimal tax, BigDecimal total, String carrier, String trackingNumber,
                                LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                LocalDateTime deliveredAt, LocalDateTime cancelledAt,
                                ShippingAddress shippingAddress) {

        public static OrderResponse from(CustomerOrder o) {
            List<OrderLine> lines = o.getItems().stream()
                    .map(i -> new OrderLine(i.getProductId(), i.getSku(), i.getProductName(),
                            i.getUnitPrice(), i.getQuantity(), i.getLineTotal()))
                    .toList();
            return new OrderResponse(o.getId(), o.getOrderNumber(), o.getUserId(), o.getStatus(), lines,
                    o.getTotalItems(), o.getSubtotal(), o.getCouponCode(), o.getDiscount(), o.getShippingFee(),
                    o.getTax(), o.getTotal(), o.getCarrier(), o.getTrackingNumber(), o.getCreatedAt(),
                    o.getPaidAt(), o.getShippedAt(), o.getDeliveredAt(), o.getCancelledAt(), o.getShippingAddress());
        }
    }

    public record TrackingEvent(String status, String note, LocalDateTime at) {
    }

    /**
     * Tracking view. Deliberately contains NO customer id, email, address, prices or items, because the same shape
     * is served by the PUBLIC endpoint GET /api/tracking/{trackingNumber}.
     */
    public record TrackingResponse(String orderNumber, String status, String carrier, String trackingNumber,
                                   List<TrackingEvent> events) {

        public static TrackingResponse from(CustomerOrder o) {
            return new TrackingResponse(o.getOrderNumber(), o.getStatus(), o.getCarrier(), o.getTrackingNumber(),
                    o.getEvents().stream().map(e -> new TrackingEvent(e.getStatus(), e.getNote(), e.getCreatedAt())).toList());
        }
    }
}
