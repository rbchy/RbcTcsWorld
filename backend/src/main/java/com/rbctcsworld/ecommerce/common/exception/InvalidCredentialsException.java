package com.rbctcsworld.ecommerce.common.exception;

/** Wrong email or password on login. Maps to HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }

    protected InvalidCredentialsException(String message) {
        super(message);
    }
}
