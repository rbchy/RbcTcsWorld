package com.rbctcsworld.ecommerce.payment;

/** An error answer from Stripe (or no answer: status 0, type api_connection_error). */
class StripeException extends RuntimeException {

    private final int status;
    private final String type;
    private final String code;
    private final String declineCode;
    private final String paymentIntentId;

    StripeException(int status, String type, String code, String declineCode, String message, String paymentIntentId) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
        this.declineCode = declineCode;
        this.paymentIntentId = paymentIntentId;
    }

    /** The card was refused - a normal business outcome, not an outage. */
    boolean isCardError() {
        return "card_error".equals(type);
    }

    int status() { return status; }
    String type() { return type; }
    String code() { return code; }
    String declineCode() { return declineCode; }
    String paymentIntentId() { return paymentIntentId; }
}
