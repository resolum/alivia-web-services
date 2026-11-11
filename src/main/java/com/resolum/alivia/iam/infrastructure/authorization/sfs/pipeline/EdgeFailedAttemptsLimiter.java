package com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounds how many wrong credentials one source may present: after {@code maxFailures} rejected ones inside a window of
 * one minute, the source is refused (429) until the window ends, without hashing or reading anything. A correct
 * credential does not count and the edge services never get near the limit (a revoked one asks every thirty seconds
 * at most).
 *
 * <p>The source is the remote address of the connection. Behind a reverse proxy that is the proxy's address unless the
 * server is told to use the forwarded one ({@code server.forward-headers-strategy}), so every client would share one
 * counter. The counters live in memory of each instance, so with several instances the limit applies to each one.
 * Memory is bounded: when {@value #MAX_TRACKED_SOURCES} sources are being tracked, new ones are let through untracked
 * rather than growing without limit.</p>
 */
@Component
public class EdgeFailedAttemptsLimiter {

    static final Duration WINDOW = Duration.ofMinutes(1);
    public static final int MAX_TRACKED_SOURCES = 10_000;

    private record Window(long startedAt, int failures) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Clock clock;

    /**
     * @param maxFailures rejected credentials a source may present per minute; {@code 0} switches the limit off
     */
    public EdgeFailedAttemptsLimiter(@Value("${alivia.iam.edge-credential.max-failed-attempts-per-minute:30}") int maxFailures,
                                     Clock clock) {
        if (maxFailures < 0) {
            throw new IllegalArgumentException("The failed attempts limit cannot be negative.");
        }
        this.maxFailures = maxFailures;
        this.clock = clock;
    }

    /**
     * @return seconds the source must wait before presenting another credential, or {@code 0} when it may
     */
    public long secondsToWait(String source) {
        if (maxFailures == 0 || source == null) {
            return 0;
        }
        var window = windows.get(source);
        var now = clock.millis();
        if (window == null || expired(window, now) || window.failures() < maxFailures) {
            return 0;
        }
        return Math.max(1, (window.startedAt() + WINDOW.toMillis() - now + 999) / 1000);
    }

    /** Counts one rejected credential from the source. */
    public void recordFailure(String source) {
        if (maxFailures == 0 || source == null) {
            return;
        }
        var now = clock.millis();
        if (windows.size() >= MAX_TRACKED_SOURCES && !windows.containsKey(source)) {
            windows.values().removeIf(window -> expired(window, now));
            if (windows.size() >= MAX_TRACKED_SOURCES) {
                return;
            }
        }
        windows.compute(source, (key, window) -> window == null || expired(window, now)
                ? new Window(now, 1) : new Window(window.startedAt(), window.failures() + 1));
    }

    private static boolean expired(Window window, long now) {
        return now - window.startedAt() >= WINDOW.toMillis();
    }
}
