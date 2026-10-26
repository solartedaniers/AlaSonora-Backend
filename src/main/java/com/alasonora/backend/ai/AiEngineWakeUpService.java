package com.alasonora.backend.ai;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.alasonora.backend.config.AiProperties;

/**
 * Despierto el motor en el pool de IA, uno a la vez, repitiendo GET /health hasta que responde o se agota el tiempo.
 * Solo lo dispara un usuario con sesión al pedir el estado, sin keep-alive; Render responde 429 si lo intenta otro servicio suyo.
 */
@Service
public class AiEngineWakeUpService {

    private static final Logger log = LoggerFactory.getLogger(AiEngineWakeUpService.class);

    private final AiEngineHealthClient healthClient;
    private final AiEngineReadiness readiness;
    private final Executor aiTaskExecutor;
    private final AiProperties.Wake wakeProperties;
    private final AtomicBoolean inProgress = new AtomicBoolean();

    public AiEngineWakeUpService(
        AiEngineHealthClient healthClient,
        AiEngineReadiness readiness,
        @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
        AiProperties aiProperties
    ) {
        this.healthClient = healthClient;
        this.readiness = readiness;
        this.aiTaskExecutor = aiTaskExecutor;
        this.wakeProperties = aiProperties.wake();
    }

    /** Devuelve false si ya había un despertar en curso o el pool de IA está lleno: en ese caso no lanzo otro. */
    public boolean wakeUp() {
        if (!inProgress.compareAndSet(false, true)) return false;
        try {
            aiTaskExecutor.execute(this::runWakeUp);
            return true;
        } catch (RejectedExecutionException rejected) {
            // Pool lleno: lo intento en la próxima consulta de estado, sin tomar el hilo de Tomcat.
            inProgress.set(false);
            log.warn("AI engine wake-up skipped: the AI pool and its queue are full");
            return false;
        } catch (RuntimeException ex) {
            inProgress.set(false);
            throw ex;
        }
    }

    private void runWakeUp() {
        long start = System.nanoTime();
        long deadline = start + wakeProperties.maxDurationMs() * 1_000_000;
        int attempts = 0;
        boolean asleepLogged = false;
        log.info("AI engine wake-up started");
        try {
            while (true) {
                attempts++;
                AiEngineHealthClient.Health health = healthClient.check();
                if (health == AiEngineHealthClient.Health.READY) {
                    readiness.markReady();
                    log.info("AI engine wake-up finished: ready after {} ms, attempts={}", AiEngineCallLog.elapsedMs(start), attempts);
                    return;
                }
                // Un solo aviso por ciclo: sin esto serían ~60 WARN iguales mientras el motor duerme.
                if (health == AiEngineHealthClient.Health.ASLEEP && !asleepLogged) {
                    asleepLogged = true;
                    log.info("AI engine is asleep (status=429): waiting for the engine to be woken by client traffic");
                }
                if (System.nanoTime() + wakeProperties.retryBackoffMs() * 1_000_000 >= deadline || !pause()) break;
            }
            log.info("AI engine wake-up finished: not ready after {} ms, attempts={}", AiEngineCallLog.elapsedMs(start), attempts);
        } finally {
            inProgress.set(false);
        }
    }

    private boolean pause() {
        try {
            Thread.sleep(wakeProperties.retryBackoffMs());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
