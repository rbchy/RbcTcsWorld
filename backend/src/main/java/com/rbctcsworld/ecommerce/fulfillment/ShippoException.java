package com.rbctcsworld.ecommerce.fulfillment;

/** An error answer from Shippo (status = HTTP status), or Shippo not reachable (status 0). */
class ShippoException extends RuntimeException {

    private final int status;

    ShippoException(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
