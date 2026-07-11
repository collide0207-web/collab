package app.collide.control.judge.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.collide.control.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exactMatchesCanonicalJsonIgnoringTrailingDebugLines() {
        Checker c = Checkers.parse("exact", mapper);
        assertThat(c.check("[1,2,3]", List.of(1, 2, 3))).isTrue();
        assertThat(c.check("debug\n[1,2,3]\n", List.of(1, 2, 3))).isTrue();
        assertThat(c.check("[1,2,4]", List.of(1, 2, 3))).isFalse();
    }

    @Test
    void nullJudgeDefaultsToExact() {
        Checker c = Checkers.parse(null, mapper);
        assertThat(c.check("42", 42)).isTrue();
    }

    @Test
    void unorderedTreatsArraysAsMultisets() {
        Checker c = Checkers.parse("unordered", mapper);
        assertThat(c.check("[1,0]", List.of(0, 1))).isTrue();
        assertThat(c.check("[0,1]", List.of(0, 1))).isTrue();
        assertThat(c.check("[0,0,1]", List.of(0, 1))).isFalse(); // multiset, not set
    }

    @Test
    void floatAcceptsWithinEps() {
        Checker c = Checkers.parse("float:1e-5", mapper);
        assertThat(c.check("2.0000001", 2.0)).isTrue();
        assertThat(c.check("2.1", 2.0)).isFalse();
    }

    @Test
    void customCheckerIsRejectedForNow() {
        assertThatThrownBy(() -> Checkers.parse("custom:two-sum", mapper))
                .isInstanceOf(ApiException.class);
    }
}
