package com.rbctcsworld.ecommerce.common.exception;

/**
 * Unknown, expired, revoked or reused refresh token. Maps to HTTP 401 (subclass of InvalidCredentialsException).
 * One message for every case, so the answer does not tell an attacker which check failed.
 */
public class InvalidRefreshTokenException extends InvalidCredentialsException {
    public InvalidRefreshTokenException() {
        super("Session expired. Please log in again.");
    }
}
