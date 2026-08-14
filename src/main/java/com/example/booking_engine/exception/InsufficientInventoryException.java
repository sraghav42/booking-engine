package com.example.booking_engine.exception;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(Long productId, int requested, int available) {
        super("Insufficient quantity for product id: " + productId + ". Requested: " + requested + ", available: " + available);
    }
}
