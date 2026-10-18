package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.order.OrderDtos.CheckoutRequest;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.order.OrderDtos.TrackingResponse;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.PayRequest;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.PaySavedRequest;
import com.rbctcsworld.ecommerce.payment.PaymentTransaction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer order API (JWT required).
 * POST /api/orders {couponCode?, shippingAddress}
 *                                   201 | 400 empty cart / no or invalid address / coupon rule | 409 stock, coupon used
 * GET  /api/orders                  200 my orders (newest first)
 * GET  /api/orders/{id}             200 | 404 (missing or not mine)
 * POST /api/orders/{id}/pay {card}  200 PAID | 400 bad/expired card | 402 declined | 404 | 409 not payable
 * POST /api/orders/{id}/pay-saved {paymentMethodId, cvv}  200 PAID | 400 expired card | 402 declined | 404 not your card | 409
 * GET  /api/orders/{id}/payments    200 payment attempts + refunds (last 4 digits only)
 * POST /api/orders/{id}/cancel      200 (refund if PAID) | 404 | 409 already cancelled / already shipped
 * GET  /api/orders/{id}/tracking    200 status timeline | 404
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(Authentication auth, @Valid @RequestBody(required = false) CheckoutRequest body) {
        return service.placeOrder(auth.getName(), body == null ? null : body.couponCode(),
                body == null ? null : body.shippingAddress());
    }

    @GetMapping
    public List<OrderResponse> mine(Authentication auth) {
        return service.myOrders(auth.getName());
    }

    @GetMapping("/{id}")
    public OrderResponse one(Authentication auth, @PathVariable Long id) {
        return service.myOrder(auth.getName(), id);
    }

    @PostMapping("/{id}/pay")
    public OrderResponse pay(Authentication auth, @PathVariable Long id, @Valid @RequestBody PayRequest card) {
        return service.pay(auth.getName(), id, card);
    }

    @PostMapping("/{id}/pay-saved")
    public OrderResponse paySaved(Authentication auth, @PathVariable Long id, @Valid @RequestBody PaySavedRequest request) {
        return service.paySaved(auth.getName(), id, request);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentTransaction> payments(Authentication auth, @PathVariable Long id) {
        return service.myPayments(auth.getName(), id);
    }

    @GetMapping("/{id}/tracking")
    public TrackingResponse tracking(Authentication auth, @PathVariable Long id) {
        return service.myTracking(auth.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(Authentication auth, @PathVariable Long id) {
        return service.cancel(auth.getName(), id);
    }
}
