package com.rbctcsworld.ecommerce.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/payments/config  200 which card form the storefront shows:
 *   cardForm = "stripe-elements" (Stripe's own form, with the publishable key) or "shop" (the shop's form, test cards).
 * Never contains a secret: the publishable key is meant for browsers.
 */
@RestController
public class PaymentConfigController {

    private final PaymentGateway gateway;

    public PaymentConfigController(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public record PaymentConfig(String provider, String cardForm, String publishableKey) {
    }

    @GetMapping("/api/payments/config")
    public PaymentConfig config() {
        String key = gateway.publishableKey();
        return new PaymentConfig(gateway.name(), key == null ? "shop" : "stripe-elements", key);
    }
}
