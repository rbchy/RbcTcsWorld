package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException;
import com.rbctcsworld.ecommerce.order.CardPaymentService.CardPaymentResponse;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.payment.StripeWebhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

/**
 * Stripe Elements (the card is entered in Stripe's form, never sent here):
 *
 * POST /api/orders/{id}/card-payment          200 {clientSecret, publishableKey} | 404 not your order
 *                                             | 409 not payable / no secure card form | 503 Stripe unreachable
 * POST /api/orders/{id}/card-payment/confirm  200 the order (PAID once Stripe says succeeded) | 404 | 409 nothing started
 * POST /api/payments/stripe/webhook           200 {received: true} | 400 missing or invalid Stripe-Signature
 *                                             | 503 webhooks not configured. No login: Stripe proves itself by signature.
 */
@RestController
public class CardPaymentController {

    private final CardPaymentService service;
    private final String webhookSecret;
    private final Clock clock;

    public CardPaymentController(CardPaymentService service,
                                 @Value("${app.payment.stripe.webhook-secret:}") String webhookSecret, Clock clock) {
        this.service = service;
        this.webhookSecret = webhookSecret;
        this.clock = clock;
    }

    @PostMapping("/api/orders/{id}/card-payment")
    public CardPaymentResponse start(Authentication auth, @PathVariable Long id) {
        return service.start(auth.getName(), id);
    }

    @PostMapping("/api/orders/{id}/card-payment/confirm")
    public OrderResponse confirm(Authentication auth, @PathVariable Long id) {
        return service.confirm(auth.getName(), id);
    }

    /** The raw body as Stripe sent it: the signature covers these exact bytes. */
    @PostMapping("/api/payments/stripe/webhook")
    public Map<String, Boolean> webhook(@RequestHeader(value = "Stripe-Signature", required = false) String signature,
                                        @RequestBody(required = false) String payload) {
        if (signature == null) {
            throw new BusinessRuleException("Missing Stripe-Signature header");
        }
        if (webhookSecret.isBlank()) {
            throw new PaymentUnavailableException("Stripe webhooks are not configured (STRIPE_WEBHOOK_SECRET)");
        }
        StripeWebhook.verify(payload, signature, webhookSecret, clock.instant());
        service.onEvent(StripeWebhook.parse(payload));
        return Map.of("received", true);
    }
}
