package com.societycentral.exception;

import com.societycentral.model.SocietyImageType;
import org.springframework.http.HttpStatus;

/**
 * Domain failure raised while validating or storing society media.
 */
public final class SocietyMediaException extends RuntimeException {

    private final HttpStatus status;

    private SocietyMediaException(
            HttpStatus status,
            String message,
            Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public static SocietyMediaException invalidFile(String message) {
        return new SocietyMediaException(
                HttpStatus.BAD_REQUEST, message, null);
    }

    public static SocietyMediaException unsupportedMediaType() {
        return new SocietyMediaException(
                HttpStatus.BAD_REQUEST,
                "Society images must be PNG, JPEG, or WebP files.",
                null);
    }

    public static SocietyMediaException unsupportedMediaType(
            SocietyImageType imageType) {
        String subject = switch (imageType) {
            case LOGO -> "logo";
            case BANNER -> "banner";
            case GALLERY_IMAGE -> "gallery image";
            case HIGHLIGHT_COVER -> "highlight cover";
        };
        return new SocietyMediaException(
                HttpStatus.BAD_REQUEST,
                "Society " + subject
                        + " must be a PNG, JPEG or WebP image.",
                null);
    }

    public static SocietyMediaException fileTooLarge(
            SocietyImageType imageType) {
        String message = switch (imageType) {
            case LOGO -> "Society logo must not exceed 2 MB.";
            case BANNER -> "Society banner must not exceed 5 MB.";
            case GALLERY_IMAGE ->
                    "Society gallery image must not exceed 5 MB.";
            case HIGHLIGHT_COVER ->
                    "Society highlight cover must not exceed 5 MB.";
        };
        return new SocietyMediaException(
                HttpStatus.PAYLOAD_TOO_LARGE, message, null);
    }

    public static SocietyMediaException unreadableImage(Throwable cause) {
        return new SocietyMediaException(
                HttpStatus.BAD_REQUEST,
                "Unable to read the uploaded society image.",
                cause);
    }

    public static SocietyMediaException storageFailure(Throwable cause) {
        return new SocietyMediaException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Society image could not be stored.",
                cause);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
