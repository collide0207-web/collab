package app.collide.control.security;

/**
 * Fixed-size rate gate keyed by an arbitrary string (typically client IP + route).
 * The in-memory implementation is per-node; the interface exists so a Redis-backed
 * implementation can be dropped in for horizontal scaling — mirroring the sync
 * server's pub/sub abstraction.
 */
public interface RateLimiter {

    /**
     * @return true if the call is allowed, false if the key has exceeded
     *         {@code maxAttempts} within the rolling {@code windowSeconds}.
     */
    boolean allow(String key, int maxAttempts, int windowSeconds);
}
