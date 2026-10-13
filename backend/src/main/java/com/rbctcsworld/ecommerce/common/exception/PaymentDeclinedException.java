package com.rbctcsworld.ecommerce.common.exception;

/** The payment provider refused the charge (card problem). Maps to HTTP 402 Payment Required. */
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
