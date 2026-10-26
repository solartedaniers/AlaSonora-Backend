package com.alasonora.backend.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.alasonora.backend.config.AiProperties;

/** El "listo" vale solo durante ai.wake.ready-fresh-ms y una clasificación fallida lo borra. */
class AiEngineReadinessTest {

    private static final long FRESH_MS = 60_000;

    private final MovableClock clock = new MovableClock();
    private final AiEngineReadiness readiness = new AiEngineReadiness(clock,
        new AiProperties(null, null, null, null, new AiProperties.Wake(1000, 1000, 10, FRESH_MS)));

    @Test
    void notReadyUntilTheEngineAnswers() {
        assertFalse(readiness.isReady());
    }

    @Test
    void readyOnlyWhileFresh() {
        readiness.markReady();
        clock.advance(Duration.ofMillis(FRESH_MS - 1));
        assertTrue(readiness.isReady());

        clock.advance(Duration.ofMillis(1));
        assertFalse(readiness.isReady());
    }

    @Test
    void unavailableForgetsTheLastReady() {
        readiness.markReady();
        readiness.markUnavailable();
        assertFalse(readiness.isReady());
    }

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
