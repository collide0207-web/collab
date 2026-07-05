package app.collide.control.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates every exception into the uniform {@link ErrorResponse} envelope. Keeps
 * messages generic on the failure paths (no stack traces, no SQL, no internal detail)
 * so nothing sensitive leaks to clients; unexpected errors are logged server-side with
 * their id at ERROR, everything else stays quiet.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Our own typed errors carry the intended status + a safe message. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return body(e.getStatus(), e.getMessage());
    }

    /** @Valid body validation failed — surface the first field error. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("validation failed");
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    /** @Validated param/path validation failed. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e) {
        return body(HttpStatus.BAD_REQUEST, "validation failed");
    }

    /** Malformed / unparseable JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "malformed request body");
    }

    /** Unique-constraint / FK violation that slipped past a pre-check (race). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "the request conflicts with existing data");
    }

    /** Optimistic-locking clash — a concurrent update won (lost-update prevention). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "the resource was modified concurrently, please retry");
    }

    /** Authenticated but not permitted (e.g. non-ADMIN hitting an ADMIN route). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "access denied");
    }

    /** Anything unforeseen: log with the stack trace, return an opaque 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error");
    }

    private static ResponseEntity<ErrorResponse> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }
}
