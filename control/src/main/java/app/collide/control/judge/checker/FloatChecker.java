package app.collide.control.judge.checker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Floating-point tolerance for scalar answers (e.g. Pow(x,n)): |actual - expected| <= eps.
 * Falls back to exact JSON equality when either side is non-numeric.
 */
public class FloatChecker implements Checker {

    private final ObjectMapper mapper;
    private final double eps;

    public FloatChecker(ObjectMapper mapper, double eps) {
        this.mapper = mapper;
        this.eps = eps;
    }

    @Override
    public boolean check(String actualStdout, Object expected) {
        try {
            JsonNode actual = mapper.readTree(Checkers.answerLine(actualStdout));
            JsonNode want = mapper.valueToTree(expected);
            if (actual.isNumber() && want.isNumber()) {
                return Math.abs(actual.asDouble() - want.asDouble()) <= eps;
            }
            return actual.equals(want);
        } catch (Exception e) {
            return false;
        }
    }
}
