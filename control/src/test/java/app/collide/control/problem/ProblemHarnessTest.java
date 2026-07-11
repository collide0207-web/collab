package app.collide.control.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProblemHarnessTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesJudgingMetadataWhenPresent() throws Exception {
        String json = """
            {"entry":"twoSum","params":[{"name":"nums","type":"int[]"}],
             "returns":"int[]","tests":[{"input":[[2,7],9],"expected":[0,1]}],
             "judge":"unordered","timeLimitMs":2000,"memoryLimitKb":65536}
            """;
        ProblemHarness h = mapper.readValue(json, ProblemHarness.class);
        assertThat(h.judge()).isEqualTo("unordered");
        assertThat(h.timeLimitMs()).isEqualTo(2000);
        assertThat(h.memoryLimitKb()).isEqualTo(65536);
    }

    @Test
    void toleratesLegacyHarnessWithoutJudgingMetadata() throws Exception {
        String json = """
            {"entry":"twoSum","params":[{"name":"nums","type":"int[]"}],
             "returns":"int[]","tests":[{"input":[[2,7],9],"expected":[0,1]}]}
            """;
        ProblemHarness h = mapper.readValue(json, ProblemHarness.class);
        assertThat(h.judge()).isNull();
        assertThat(h.timeLimitMs()).isNull();
        assertThat(h.entry()).isEqualTo("twoSum");
    }
}
