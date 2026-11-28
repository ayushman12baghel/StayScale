package com.stayscale.core.exception;

/** Raised when a mutating endpoint is called without an Idempotency-Key header (HTTP 400). */
public class MissingIdempotencyKeyException extends RuntimeException {
    public MissingIdempotencyKeyException(String message) {
        super(message);
    }
}
