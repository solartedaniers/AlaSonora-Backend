package com.alasonora.backend.ai;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.alasonora.backend.config.AiProperties;

/**
 * Despierta el motor en el pool de IA, nunca en un hilo de Tomcat: un solo despertar a la vez, que
 * repite GET /health hasta que responde o se agota ai.wake.max-duration-ms. Solo lo dispara un
 * usuario con sesión al pedir el estado; no hay keep-alive ni consultas programadas.
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

    /** Devuelve false si ya había un despertar en curso: en ese caso no lanzo otro. */
    public boolean wakeUp() {
        if (!inProgress.compareAndSet(false, true)) return false;
        try {
            aiTaskExecutor.execute(this::runWakeUp);
            return true;
        } catch (RuntimeException ex) {
            inProgress.set(false);
            throw ex;
        }
    }

    private void runWakeUp() {
        long start = System.nanoTime();
        long deadline = start + wakeProperties.maxDurationMs() * 1_000_000;
        int attempts = 0;
        log.info("AI engine wake-up started");
        try {
            while (true) {
                attempts++;
                if (healthClient.isReady()) {
                    readiness.markReady();
                    log.info("AI engine wake-up finished: ready after {} ms, attempts={}", AiEngineCallLog.elapsedMs(start), attempts);
                    return;
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
