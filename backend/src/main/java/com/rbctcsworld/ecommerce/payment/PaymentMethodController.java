package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.payment.PaymentDtos.AddCardRequest;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.MethodResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The signed-in customer's wallet (JWT required). Responses never contain the card number or the provider token.
 * GET    /api/payment-methods               -> 200 [default first, then newest]
 * POST   /api/payment-methods               -> 201 | 400 bad/expired/unsupported card | 409 twin or wallet full (5)
 * POST   /api/payment-methods/{id}/default  -> 200 | 404
 * DELETE /api/payment-methods/{id}          -> 204 | 404
 */
@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService service;

    public PaymentMethodController(PaymentMethodService service) {
        this.service = service;
    }

    @GetMapping
    public List<MethodResponse> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MethodResponse add(Authentication auth, @Valid @RequestBody AddCardRequest r) {
        return service.add(auth.getName(), r);
    }

    @PostMapping("/{id}/default")
    public MethodResponse makeDefault(Authentication auth, @PathVariable long id) {
        return service.makeDefault(auth.getName(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable long id) {
        service.delete(auth.getName(), id);
    }
}
