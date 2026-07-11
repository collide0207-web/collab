package app.collide.control.judge;

/**
 * The authoritative outcome of a Submit: which verdict, how many of the hidden cases passed,
 * the index of the first failing case (or -1 on AC), and the worst per-case runtime observed.
 * Hidden inputs are never part of this record — only the failing index is exposed (spec §4).
 */
public record Verdict(VerdictStatus status, int passed, int total, int failingCaseIndex, long maxRuntimeMs) {

    public static Verdict compileError() {
        return new Verdict(VerdictStatus.CE, 0, 0, -1, 0);
    }

    public static Verdict accepted(int total, long maxRuntimeMs) {
        return new Verdict(VerdictStatus.AC, total, total, -1, maxRuntimeMs);
    }

    public static Verdict failed(VerdictStatus status, int passed, int total, int failingIndex, long maxRuntimeMs) {
        return new Verdict(status, passed, total, failingIndex, maxRuntimeMs);
    }

    /** Verdict codes. AC=accepted, WA=wrong answer, TLE=time limit, RE=runtime error, CE=compile error. */
    public enum VerdictStatus {
        AC,
        WA,
        TLE,
        RE,
        CE
    }
}
