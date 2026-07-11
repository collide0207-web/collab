package app.collide.control.judge;

import java.util.List;

/** One hidden case: {@code input} in param order (wire JSON values), {@code expected} return. */
public record TestCase(List<Object> input, Object expected) {}
