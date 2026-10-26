package com.alasonora.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.alasonora.backend.config.AiProperties;

/**
 * Al arrancar dejo en el log el host efectivo del motor. Si la URL no sirve solo aviso: el backend
 * arranca igual y el resto de la app funciona sin IA.
 */
@Component
public class AiEngineStartupReport {

    private static final Logger log = LoggerFactory.getLogger(AiEngineStartupReport.class);

    private final AiProperties.Engine engine;

    public AiEngineStartupReport(AiProperties aiProperties) {
        this.engine = aiProperties.engine();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        String host = engine.host();
        if (host == null) {
            log.warn("AI engine base URL is not an absolute http(s) URL; AI calls will fail until AI_ENGINE_BASE_URL is fixed");
        } else {
            log.info("AI engine host: {}", host);
        }
    }
}
