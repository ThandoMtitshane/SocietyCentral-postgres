package com.societycentral.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Generic response envelope returned by every controller endpoint.
 * <p>
 * Example success response body:
 * <pre>
 * {
 *   "type": "SUCCESS",
 *   "message": "Event retrieved successfully",
 *   "data": { ... EventResponse ... }
 * }
 * </pre>
 * Example error response body:
 * <pre>
 * {
 *   "type": "ERROR",
 *   "message": "Event not found",
 *   "data": null
 * }
 * </pre>
 *
 * @param <T> the type of the payload returned in {@code data}
 *  Please always use this ApiResponse entity whenever you are responding to an API call because the front-end relies on it.
 */
@Getter
@Setter
public class ApiResponse<T> {

    private ResponseType type;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(ResponseType type, String message, T data) {
        this.type = type;
        this.message = message;
        this.data = data;
    }

    // ------------------------------------------------------------
    // Convenience factory methods
    // ------------------------------------------------------------

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ResponseType.SUCCESS, message, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResponseType.SUCCESS, "Request successful", data);
    }

    public static <T> ApiResponse<T> warning(String message, T data) {
        return new ApiResponse<>(ResponseType.WARNING, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ResponseType.ERROR, message, null);
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(ResponseType.ERROR, message, data);
    }

}