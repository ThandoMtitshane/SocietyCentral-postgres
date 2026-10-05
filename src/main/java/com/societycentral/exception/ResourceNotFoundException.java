package com.societycentral.exception;

/**
 * Indicates that a requested domain resource does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
