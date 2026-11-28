package com.stayscale.core.exception;

/** Raised when the same Idempotency-Key is reused with a different request payload (HTTP 422). */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
