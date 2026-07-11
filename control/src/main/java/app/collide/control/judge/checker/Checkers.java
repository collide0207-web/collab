package app.collide.control.judge.checker;

import app.collide.control.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Parses a harness {@code judge} spec into a {@link Checker}. Null/blank → exact. */
public final class Checkers {

    private Checkers() {}

    public static Checker parse(String judge, ObjectMapper mapper) {
        if (judge == null || judge.isBlank() || judge.equals("exact")) {
            return new ExactChecker(mapper);
        }
        if (judge.equals("unordered")) {
            return new UnorderedChecker(mapper);
        }
        if (judge.startsWith("float:")) {
            double eps;
            try {
                eps = Double.parseDouble(judge.substring("float:".length()));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("invalid float checker eps: " + judge);
            }
            return new FloatChecker(mapper, eps);
        }
        if (judge.startsWith("custom:")) {
            throw ApiException.badRequest("custom checkers are not supported yet: " + judge);
        }
        throw ApiException.badRequest("unknown judge spec: " + judge);
    }

    /** The program's answer line: last non-blank line of stdout (tolerates trailing debug). */
    static String answerLine(String stdout) {
        if (stdout == null) {
            return "";
        }
        String[] lines = stdout.strip().split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String s = lines[i].strip();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return "";
    }
}
