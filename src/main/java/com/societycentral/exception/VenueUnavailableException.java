package com.societycentral.exception;

/**
 * Signals that the selected venue cannot be used for an event operation.
 */
public class VenueUnavailableException extends RuntimeException {

    /**
     * Creates the scheduling-conflict exception with the stable API message.
     */
    public VenueUnavailableException() {
        super("The selected venue is not available for the requested date and time.");
    }

    /**
     * Creates a venue-unavailability exception with a rule-specific message.
     *
     * @param message safe message returned through the standard API envelope
     */
    public VenueUnavailableException(String message) {
        super(message);
    }
}
