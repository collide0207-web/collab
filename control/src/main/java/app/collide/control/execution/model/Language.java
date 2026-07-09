package app.collide.control.execution.model;

import app.collide.control.common.ApiException;
import java.util.Locale;

/**
 * The exact set of languages the engine supports. {@link #fromWire} is an allow-list —
 * it never dispatches on an arbitrary client-supplied string, so an unsupported/unknown
 * value always fails closed with a 400 rather than reaching a process executor.
 */
public enum Language {
    PYTHON,
    JAVASCRIPT,
    CPP,
    JAVA;

    public static Language fromWire(String value) {
        if (value == null) {
            throw ApiException.badRequest("language is required");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "python", "python3", "py" -> PYTHON;
            case "javascript", "node", "js" -> JAVASCRIPT;
            case "cpp", "c++" -> CPP;
            case "java" -> JAVA;
            default -> throw ApiException.badRequest("unsupported language: " + value);
        };
    }
}
