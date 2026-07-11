package app.collide.control.judge.checker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Multiset equality for array answers (e.g. Two Sum indices, group-anagrams): same elements
 * with the same multiplicities, order-insensitive. Elements are compared by their canonical
 * JSON text, so nested arrays (list of groups) work too. Non-array answers fall back to exact.
 */
public class UnorderedChecker implements Checker {

    private final ObjectMapper mapper;

    public UnorderedChecker(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean check(String actualStdout, Object expected) {
        try {
            JsonNode actual = mapper.readTree(Checkers.answerLine(actualStdout));
            JsonNode want = mapper.valueToTree(expected);
            if (!actual.isArray() || !want.isArray()) {
                return actual.equals(want);
            }
            return canonicalSorted(actual).equals(canonicalSorted(want));
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> canonicalSorted(JsonNode arr) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) {
            out.add(n.toString());
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }
}
