package app.collide.control.judge;

/** Submit payload: the language and the user's full solution source. */
public record SubmitRequest(String language, String sourceCode) {}
