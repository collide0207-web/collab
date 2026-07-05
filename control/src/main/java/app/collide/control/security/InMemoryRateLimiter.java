package app.collide.control.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed-window, in-process rate limiter. Good enough for a single node and for local
 * dev; swap for a Redis-backed {@link RateLimiter} when running multiple replicas.
 *
 * Each key tracks a window-start second and a count. When the window elapses it resets.
 * A coarse size cap keeps memory bounded under a flood of distinct keys (e.g. spoofed
 * IPs) without a background sweeper.
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    private static final int MAX_KEYS = 100_000;

    private static final class Window {
        long startEpochSecond;
        int count;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean allow(String key, int maxAttempts, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        if (windows.size() > MAX_KEYS) windows.clear(); // crude overflow guard

        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            if (now - w.startEpochSecond >= windowSeconds) {
                w.startEpochSecond = now;
                w.count = 0;
            }
            w.count++;
            return w.count <= maxAttempts;
        }
    }
}
