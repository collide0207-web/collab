package app.collide.control.execution.ws;

import app.collide.control.execution.ExecutionResult;
import app.collide.control.execution.model.ExecutionStatus;
import java.util.UUID;

/**
 * One frame sent over {@code /ws/execution/{id}}: an incremental stdout/stderr chunk, a
 * status transition, or the final result. Rendered to JSON by hand (no ObjectMapper) since
 * this is sent outside Spring MVC's message-converter pipeline — same reasoning as
 * {@link app.collide.control.common.ErrorResponse#toJson()}.
 */
public record ExecutionEvent(String type, String executionId, String chunk, ExecutionStatus status, ExecutionResult result) {

    public static ExecutionEvent stdout(UUID executionId, String chunk) {
        return new ExecutionEvent("stdout", executionId.toString(), chunk, null, null);
    }

    public static ExecutionEvent stderr(UUID executionId, String chunk) {
        return new ExecutionEvent("stderr", executionId.toString(), chunk, null, null);
    }

    public static ExecutionEvent status(UUID executionId, ExecutionStatus status) {
        return new ExecutionEvent("status", executionId.toString(), null, status, null);
    }

    public static ExecutionEvent result(UUID executionId, ExecutionResult result) {
        return new ExecutionEvent("result", executionId.toString(), null, result.status(), result);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder(64);
        json.append("{\"type\":").append(quote(type)).append(",\"executionId\":").append(quote(executionId));
        if (chunk != null) {
            json.append(",\"chunk\":").append(quote(chunk));
        }
        if (status != null) {
            json.append(",\"status\":").append(quote(status.name()));
        }
        if (result != null) {
            json.append(",\"result\":{\"stdout\":").append(quote(result.stdout()))
                    .append(",\"stderr\":").append(quote(result.stderr()))
                    .append(",\"exitCode\":").append(result.exitCode() == null ? "null" : result.exitCode())
                    .append(",\"executionTimeMs\":").append(result.executionTimeMs())
                    .append(",\"stdoutTruncated\":").append(result.stdoutTruncated())
                    .append(",\"stderrTruncated\":").append(result.stderrTruncated())
                    .append("}");
        }
        return json.append("}").toString();
    }

    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
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
