package app.collide.control.judge;

import static org.assertj.core.api.Assertions.assertThat;

import app.collide.control.judge.Verdict.VerdictStatus;
import org.junit.jupiter.api.Test;

class VerdictTest {

    @Test
    void acceptedFillsFailingIndexWithMinusOneAndFullPassCount() {
        Verdict v = Verdict.accepted(100, 42);
        assertThat(v.status()).isEqualTo(VerdictStatus.AC);
        assertThat(v.passed()).isEqualTo(100);
        assertThat(v.total()).isEqualTo(100);
        assertThat(v.failingCaseIndex()).isEqualTo(-1);
        assertThat(v.maxRuntimeMs()).isEqualTo(42);
    }

    @Test
    void compileErrorHasNoCasesRun() {
        Verdict v = Verdict.compileError();
        assertThat(v.status()).isEqualTo(VerdictStatus.CE);
        assertThat(v.total()).isZero();
        assertThat(v.failingCaseIndex()).isEqualTo(-1);
    }

    @Test
    void failedCarriesFirstFailingIndexAndPassCountBeforeIt() {
        Verdict v = Verdict.failed(VerdictStatus.WA, 41, 100, 41, 12);
        assertThat(v.status()).isEqualTo(VerdictStatus.WA);
        assertThat(v.passed()).isEqualTo(41);
        assertThat(v.failingCaseIndex()).isEqualTo(41);
    }
}
