package app.collide.control.judge.checker;

/**
 * Decides whether a program's stdout is a correct answer for one hidden case. Both tiers key
 * off the harness {@code judge} string (spec §4); the judge parses it once per submission via
 * {@link Checkers}. Implementations compare the last non-blank stdout line against the bundle's
 * expected value, tolerating trailing debug output the same way the client Run tier does.
 */
public interface Checker {
    boolean check(String actualStdout, Object expected);
}
