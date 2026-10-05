package com.societycentral.exception;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.model.EventImageType;
import com.societycentral.model.SocietyImageType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Catches exceptions thrown from controllers/services and converts them
 * into a consistent ApiResponse&lt;Void&gt; error body, so the frontend
 * never sees a raw stack trace.
 * <p>
 * Add more @ExceptionHandler methods here as new exception types are
 * introduced by services (e.g. a custom ResourceNotFoundException once
 * controllers are built).
 * so instead of throwing exceptions please utilise this class and add more handlers
 * if the current ones do not satisfy your need.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Converts event-media domain failures to their intended API status.
     *
     * @param ex event-media failure
     * @return standard API error response
     */
    @ExceptionHandler(EventMediaException.class)
    public ResponseEntity<ApiResponse<Void>> handleEventMedia(EventMediaException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Converts society-media validation failures to their intended status.
     */
    @ExceptionHandler(SocietyMediaException.class)
    public ResponseEntity<ApiResponse<Void>> handleSocietyMedia(
            SocietyMediaException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage()));
    }


    /**
     * Reports Spring's multipart limit before controller invocation.
     *
     * @param ex multipart size failure
     * @return payload-too-large API response
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String message;
        if (isExecutiveSocietyProfileMediaRequest(requestUri, "/logo")) {
            message = "Society logo must not exceed 2 MB.";
        } else if (isExecutiveSocietyProfileMediaRequest(
                requestUri, "/banner")) {
            message = "Society banner must not exceed 5 MB.";
        } else {
            message = "Event image must not exceed 5 MB.";
        }
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(message));
    }

    /**
     * Reports missing event-media multipart fields.
     *
     * @param ex missing request field
     * @return bad-request API response
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingMultipartField(
            MissingServletRequestPartException ex,
            HttpServletRequest request) {
        if (isExecutiveSocietyProfileMediaRequest(
                request.getRequestURI(), "/logo")
                || isExecutiveSocietyProfileMediaRequest(
                request.getRequestURI(), "/banner")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            "Multipart field 'file' is required."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "Multipart fields 'file' and 'imageType' are required."));
    }

    /**
     * Reports a missing required query parameter.
     *
     * @param ex missing request parameter
     * @return bad-request API response
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(
            MissingServletRequestParameterException ex) {
        if ("imageType".equals(ex.getParameterName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            "Multipart fields 'file' and 'imageType' are required."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "Required request parameter '"
                                + ex.getParameterName()
                                + "' is missing."));
    }

    /**
     * Converts invalid enum and other request-parameter values into stable API errors.
     *
     * @param ex request-parameter conversion failure
     * @return bad-request API response
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String message;
        if ("imageType".equals(ex.getName())
                && ex.getRequiredType() == EventImageType.class) {
            message = "imageType must be POSTER or BANNER.";
        } else if ("imageType".equals(ex.getName())
                && ex.getRequiredType() == SocietyImageType.class) {
            message = "imageType must be LOGO or BANNER.";
        } else {
            message = "Invalid request parameter: " + ex.getName();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    /**
     * Prevents missing API routes from leaking Spring static-resource details.
     *
     * @param ex missing-resource failure
     * @return not-found API response
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Resource not found."));
    }

    /**
     * Reports malformed multipart requests.
     *
     * @param ex multipart parsing failure
     * @return bad-request API response
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("The multipart request could not be processed."));
    }

    /**
     * Reports request DTO validation messages through the standard envelope.
     *
     * @param ex bean-validation failure
     * @return bad-request API response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(
                        error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed.", fieldErrors));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "The submitted society profile contains a value that cannot be stored."));
    }

    /**
     * Reports a missing or malformed JSON request body as invalid input.
     *
     * @param ex request-body parsing failure
     * @return bad-request API response
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Malformed request body."));
    }

    /**
     * Reports requests sent with an unsupported HTTP content type.
     *
     * @param ex unsupported request content type
     * @return unsupported-media-type API response
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Unsupported request content type."));
    }

    /**
     * Reports authorization failures raised by method security.
     *
     * @param ex access-denied failure
     * @return forbidden API response
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        "You do not have permission to perform this action."));
    }

    /**
     * Converts a normal venue scheduling conflict to HTTP 409.
     *
     * @param ex venue availability conflict
     * @return conflict API response
     */
    @ExceptionHandler(VenueUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleVenueUnavailable(
            VenueUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Converts missing domain resources to HTTP 404.
     *
     * @param ex resource lookup failure
     * @return not-found API response
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Converts authenticated society-ownership failures to HTTP 403.
     *
     * @param ex society-scoped authorization failure
     * @return forbidden API response
     */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenOperation(
            ForbiddenOperationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled request failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred."));
    }

    private boolean isExecutiveSocietyProfileMediaRequest(
            String requestUri,
            String mediaSuffix) {
        return requestUri != null
                && requestUri.startsWith("/api/executive/societ")
                && requestUri.contains("/profile")
                && requestUri.endsWith(mediaSuffix);
    }
}
