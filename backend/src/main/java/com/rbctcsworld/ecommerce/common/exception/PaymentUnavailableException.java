package com.rbctcsworld.ecommerce.common.exception;

/**
 * The payment provider could not be reached or answered with an error that is not about the card.
 * HTTP 503. Extends PaymentDeclinedException only so the existing noRollbackFor keeps the FAILED attempt
 * in the database; the exception handler maps this subclass to 503, not 402.
 */
public class PaymentUnavailableException extends PaymentDeclinedException {
    public PaymentUnavailableException(String message) {
        super(message);
    }
}
