package com.rbctcsworld.ecommerce.checkout;

import com.rbctcsworld.ecommerce.order.OrderDtos.CheckoutRequest;
import com.rbctcsworld.ecommerce.order.OrderDtos.QuoteResponse;
import com.rbctcsworld.ecommerce.order.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/checkout/quote {couponCode?, shippingAddress?} -> 200 price breakdown for the current cart
 *   (with an address the tax is exact; without one a tax provider may only estimate it: taxEstimated)
 *                                         | 400 empty cart / invalid, expired or below-minimum coupon
 *                                         | 409 coupon already used / usage limit reached
 * Safe to call any number of times: nothing is saved.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final OrderService orders;

    public CheckoutController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(Authentication auth, @Valid @RequestBody(required = false) CheckoutRequest body) {
        return orders.quote(auth.getName(), body == null ? null : body.couponCode(),
                body == null ? null : body.shippingAddress());
    }
}
