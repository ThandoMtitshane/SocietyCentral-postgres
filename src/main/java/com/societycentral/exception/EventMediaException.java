package com.societycentral.exception;

import com.societycentral.model.EventImageType;
import org.springframework.http.HttpStatus;

/**
 * Domain exception for event-media validation, authorization, and storage
 * failures.
 */
public final class EventMediaException extends RuntimeException {

    /**
     * Machine-readable category for an event-media failure.
     */
    public enum Reason {
        INVALID_FILE,
        INVALID_IMAGE_TYPE,
        INVALID_DIMENSIONS,
        UNSUPPORTED_MEDIA_TYPE,
        FILE_TOO_LARGE,
        UNREADABLE_IMAGE,
        STORAGE_FAILURE,
        UNAUTHORISED_EXECUTIVE
    }

    private final Reason reason;
    private final HttpStatus status;

    private EventMediaException(
            Reason reason,
            HttpStatus status,
            String message,
            Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.status = status;
    }

    /**
     * Creates a bad-request exception for a missing or empty file.
     *
     * @param message client-safe validation message
     * @return event-media exception
     */
    public static EventMediaException invalidFile(String message) {
        return new EventMediaException(
                Reason.INVALID_FILE,
                HttpStatus.BAD_REQUEST,
                message,
                null);
    }

    /**
     * Creates a bad-request exception for an invalid image type value.
     *
     * @return event-media exception
     */
    public static EventMediaException invalidImageType() {
        return new EventMediaException(
                Reason.INVALID_IMAGE_TYPE,
                HttpStatus.BAD_REQUEST,
                "imageType must be POSTER or BANNER.",
                null);
    }

    /**
     * Creates a bad-request exception when the frontend-processed output does
     * not match the final dimensions required for the selected image type.
     *
     * @param imageType selected event image type
     * @return event-media exception
     */
    public static EventMediaException invalidDimensions(EventImageType imageType) {
        String message = switch (imageType) {
            case POSTER -> "Processed poster must be exactly 1080 \u00D7 1350 pixels.";
            case BANNER -> "Processed banner must be exactly 1500 \u00D7 500 pixels.";
        };
        return new EventMediaException(
                Reason.INVALID_DIMENSIONS,
                HttpStatus.BAD_REQUEST,
                message,
                null);
    }

    /**
     * Creates an unsupported-media-type exception.
     *
     * @param message client-safe validation message
     * @return event-media exception
     */
    public static EventMediaException unsupportedMediaType(String message) {
        return new EventMediaException(
                Reason.UNSUPPORTED_MEDIA_TYPE,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                message,
                null);
    }

    /**
     * Creates a payload-too-large exception for the five-megabyte business
     * limit.
     *
     * @return event-media exception
     */
    public static EventMediaException fileTooLarge() {
        return new EventMediaException(
                Reason.FILE_TOO_LARGE,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Event image must not exceed 5 MB.",
                null);
    }

    /**
     * Creates a bad-request exception when ImageIO cannot decode the file.
     *
     * @param cause underlying decoder failure, if available
     * @return event-media exception
     */
    public static EventMediaException unreadableImage(Throwable cause) {
        return new EventMediaException(
                Reason.UNREADABLE_IMAGE,
                HttpStatus.BAD_REQUEST,
                "Unable to read the uploaded image.",
                cause);
    }

    /**
     * Creates an internal-server-error exception for local storage failures.
     *
     * @param cause underlying storage failure
     * @return event-media exception
     */
    public static EventMediaException storageFailure(Throwable cause) {
        return new EventMediaException(
                Reason.STORAGE_FAILURE,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Event image could not be stored.",
                cause);
    }

    /**
     * Creates a forbidden exception when the authenticated account has no
     * active executive role.
     *
     * @return event-media exception
     */
    public static EventMediaException unauthorisedExecutive() {
        return new EventMediaException(
                Reason.UNAUTHORISED_EXECUTIVE,
                HttpStatus.FORBIDDEN,
                "Only an active society executive may upload event media.",
                null);
    }

    /**
     * Returns the domain reason for the failure.
     *
     * @return failure reason
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * Returns the HTTP status associated with the domain failure.
     *
     * @return response status
     */
    public HttpStatus getStatus() {
        return status;
    }
}
