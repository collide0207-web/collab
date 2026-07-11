package app.collide.control.judge.checker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Canonical JSON equality. Parses both the answer line and the expected value into a JsonNode
 * tree and compares structurally, so {@code [1,2]} equals {@code [1, 2]} and {@code 2.0}
 * equals {@code 2} — insulated from a language driver's incidental spacing.
 */
public class ExactChecker implements Checker {

    private final ObjectMapper mapper;

    public ExactChecker(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean check(String actualStdout, Object expected) {
        try {
            JsonNode actual = mapper.readTree(Checkers.answerLine(actualStdout));
            JsonNode want = mapper.valueToTree(expected);
            return actual.equals(want);
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
