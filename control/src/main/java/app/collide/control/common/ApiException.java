package app.collide.control.common;

import org.springframework.http.HttpStatus;

/** Application error carrying an HTTP status. Translated to JSON by the handler. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, msg);
    }

    public static ApiException unauthorized(String msg) {
        return new ApiException(HttpStatus.UNAUTHORIZED, msg);
    }

    public static ApiException forbidden(String msg) {
        return new ApiException(HttpStatus.FORBIDDEN, msg);
    }

    public static ApiException notFound(String msg) {
        return new ApiException(HttpStatus.NOT_FOUND, msg);
    }

    public static ApiException conflict(String msg) {
        return new ApiException(HttpStatus.CONFLICT, msg);
    }

    /** 423 — account temporarily locked after too many failed logins. */
    public static ApiException locked(String msg) {
        return new ApiException(HttpStatus.LOCKED, msg);
    }

    /** 429 — client exceeded a rate limit. */
    public static ApiException tooManyRequests(String msg) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, msg);
    }

    /** 503 — a dependency (e.g. Google's token endpoint) is unavailable. */
    public static ApiException serviceUnavailable(String msg) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, msg);
    }
}
