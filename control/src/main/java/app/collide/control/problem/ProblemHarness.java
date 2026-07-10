package app.collide.control.problem;

import java.util.List;

/**
 * Optional test-runner metadata for a problem. When present, the frontend wraps the
 * user's {@code Solution} in a generated driver (main + I/O), feeds each test case's
 * inputs, and compares output against {@code expected} — the LeetCode-style Run flow.
 * When absent, Run just executes the submitted source as-is.
 *
 * Stored as JSONB (concrete records, NOT Jackson JsonNode, so responses serialize as
 * real JSON — see {@link Problem}). Input/expected values are stored as their natural
 * JSON types (number, string, array, …); the declared param {@code type} tells the
 * frontend how to turn each into a native literal per language.
 */
public record ProblemHarness(
        String entry,
        List<Param> params,
        String returns,
        List<Test> tests) {

    /** One positional parameter: {@code name} for display, {@code type} drives codegen. */
    public record Param(String name, String type) {}

    /** One example case: {@code input} in param order, {@code expected} return value. */
    public record Test(List<Object> input, Object expected) {}
}
