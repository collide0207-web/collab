package app.collide.control.execution;

/** Wire shape of POST /execute. Size/blank validation happens in {@link ExecutionService}. */
public record ExecutionRequest(String language, String sourceCode, String stdin) {
}
