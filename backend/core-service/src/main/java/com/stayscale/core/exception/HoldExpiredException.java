package com.stayscale.core.exception;

/** Raised when a 10-minute checkout hold has expired or been consumed (HTTP 410 Gone). */
public class HoldExpiredException extends RuntimeException {
    public HoldExpiredException(String message) {
        super(message);
    }
}
