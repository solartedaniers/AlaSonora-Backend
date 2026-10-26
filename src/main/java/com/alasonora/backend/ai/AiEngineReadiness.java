package com.alasonora.backend.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.alasonora.backend.config.AiProperties;

/**
 * Último estado conocido del motor. Recuerdo cuándo respondió "ok" y lo doy por listo solo durante
 * ai.wake.ready-fresh-ms: así /api/ai/status contesta al instante sin consultar al motor.
 */
@Component
public class AiEngineReadiness {

    private final Clock clock;
    private final Duration readyFresh;
    private final AtomicReference<Instant> lastReadyAt = new AtomicReference<>();

    public AiEngineReadiness(Clock clock, AiProperties aiProperties) {
        this.clock = clock;
        this.readyFresh = Duration.ofMillis(aiProperties.wake().readyFreshMs());
    }

    public boolean isReady() {
        Instant readyAt = lastReadyAt.get();
        return readyAt != null && clock.instant().isBefore(readyAt.plus(readyFresh));
    }

    public void markReady() {
        lastReadyAt.set(clock.instant());
    }

    /** Una clasificación encontró el motor apagado: el próximo /api/ai/status vuelve a despertarlo. */
    public void markUnavailable() {
        lastReadyAt.set(null);
    }
}
