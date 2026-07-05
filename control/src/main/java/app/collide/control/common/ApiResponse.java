package app.collide.control.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform success envelope: {@code { "success": true, "message": ..., "data": ... }}.
 * Errors use {@link ErrorResponse} instead (produced by the global handler). Null
 * fields are omitted so, e.g., a message-only response doesn't carry {@code "data": null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null);
    }
}
