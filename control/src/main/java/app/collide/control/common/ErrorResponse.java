package app.collide.control.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Uniform error envelope: {@code { "success": false, "error": ..., "message": ...,
 * "status": ..., "timestamp": ... }}. Messages are deliberately generic on the auth
 * paths (e.g. "invalid credentials") to avoid account enumeration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        String error,
        String message,
        int status,
        String timestamp) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(false, status.getReasonPhrase(), message, status.value(), Instant.now().toString());
    }

    /**
     * Minimal, dependency-free JSON rendering. Used by the security filter-chain
     * handlers, which write directly to the servlet response (outside the MVC message
     * converters, so we don't rely on a specific Jackson version being on the classpath).
     */
    public String toJson() {
        return "{"
                + "\"success\":false,"
                + "\"error\":" + quote(error) + ","
                + "\"message\":" + quote(message) + ","
                + "\"status\":" + status + ","
                + "\"timestamp\":" + quote(timestamp)
                + "}";
    }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}

