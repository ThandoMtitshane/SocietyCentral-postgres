package com.societycentral.exception;

/**
 * Indicates that an authenticated user is not authorised for a
 * society-scoped operation.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
