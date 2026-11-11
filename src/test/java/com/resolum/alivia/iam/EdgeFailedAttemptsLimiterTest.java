package com.resolum.alivia.iam;

import com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline.EdgeFailedAttemptsLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EdgeFailedAttemptsLimiterTest {

    private static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T12:00:00Z");

        void advance(Duration time) {
            now = now.plus(time);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final EdgeFailedAttemptsLimiter limiter = new EdgeFailedAttemptsLimiter(3, clock);

    private void fail(String source, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(source);
        }
    }

    @Test
    void aSourceBelowTheLimitIsNeverMadeToWait() {
        fail("10.0.0.1", 2);

        assertEquals(0, limiter.secondsToWait("10.0.0.1"));
        assertEquals(0, limiter.secondsToWait("10.0.0.2"));
    }

    @Test
    void reachingTheLimitRefusesTheSourceUntilItsWindowEnds() {
        fail("10.0.0.1", 3);

        assertEquals(60, limiter.secondsToWait("10.0.0.1"));
        clock.advance(Duration.ofSeconds(20));
        assertEquals(40, limiter.secondsToWait("10.0.0.1"));
        clock.advance(Duration.ofMillis(39_500));
        assertEquals(1, limiter.secondsToWait("10.0.0.1")); // never rounds down to zero while still refused
        clock.advance(Duration.ofMillis(500));
        assertEquals(0, limiter.secondsToWait("10.0.0.1"));
    }

    @Test
    void sourcesAreCountedApartAndAnExpiredWindowStartsOver() {
        fail("10.0.0.1", 3);
        fail("10.0.0.2", 2);
        assertEquals(0, limiter.secondsToWait("10.0.0.2"));

        clock.advance(Duration.ofMinutes(2));
        fail("10.0.0.1", 2); // the old failures no longer count
        assertEquals(0, limiter.secondsToWait("10.0.0.1"));
        fail("10.0.0.1", 1);
        assertEquals(60, limiter.secondsToWait("10.0.0.1"));
    }

    @Test
    void failuresDoNotExtendTheWindowOfASourceThatIsAlreadyRefused() {
        fail("10.0.0.1", 3);
        clock.advance(Duration.ofSeconds(30));
        fail("10.0.0.1", 5);

        assertEquals(30, limiter.secondsToWait("10.0.0.1"));
    }

    @Test
    void aLimitOfZeroSwitchesItOffAndANullSourceIsIgnored() {
        var off = new EdgeFailedAttemptsLimiter(0, clock);
        for (int i = 0; i < 100; i++) {
            off.recordFailure("10.0.0.1");
        }

        assertEquals(0, off.secondsToWait("10.0.0.1"));
        limiter.recordFailure(null);
        assertEquals(0, limiter.secondsToWait(null));
        assertThrows(IllegalArgumentException.class, () -> new EdgeFailedAttemptsLimiter(-1, clock));
    }

    @Test
    void memoryIsBoundedNewSourcesBeyondTheCapAreLetThroughAndExpiredOnesAreForgotten() {
        for (int i = 0; i < EdgeFailedAttemptsLimiter.MAX_TRACKED_SOURCES; i++) {
            limiter.recordFailure("source-" + i);
        }
        // The table is full of live windows: a new source is not tracked, so it is never refused.
        fail("newcomer", 10);
        assertEquals(0, limiter.secondsToWait("newcomer"));
        // A tracked source still is counted.
        fail("source-0", 2);
        assertEquals(60, limiter.secondsToWait("source-0"));

        // Once the windows expire there is room again.
        clock.advance(Duration.ofMinutes(2));
        fail("newcomer", 3);
        assertEquals(60, limiter.secondsToWait("newcomer"));
    }
}
