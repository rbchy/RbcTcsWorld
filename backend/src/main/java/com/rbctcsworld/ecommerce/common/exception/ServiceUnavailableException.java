package com.rbctcsworld.ecommerce.common.exception;

/** An outside service the request needs (a carrier, ...) does not answer right now: 503 with Retry-After. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
