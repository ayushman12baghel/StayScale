package com.stayscale.core.exception;

/** Raised when inventory is sold out or concurrent lock acquisition fails (HTTP 409 Conflict). */
public class InventoryConflictException extends RuntimeException {
    public InventoryConflictException(String message) {
        super(message);
    }
}
