package com.rbctcsworld.ecommerce.tax;

/** An error answer from Stripe Tax, or Stripe not reachable (status 0). */
class StripeTaxException extends RuntimeException {

    private final int status;
    private final String code;

    StripeTaxException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    int status() {
        return status;
    }

    String code() {
        return code;
    }
}
