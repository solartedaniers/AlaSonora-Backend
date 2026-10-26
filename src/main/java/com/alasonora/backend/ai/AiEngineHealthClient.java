package com.alasonora.backend.ai;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.alasonora.backend.config.AiProperties;

/**
 * Consulta el /health del motor. Solo la usa {@link AiEngineWakeUpService}, en segundo plano:
 * su timeout largo (ai.wake.attempt-timeout-ms) deja que Render retenga la petición mientras arranca.
 */
@Component
public class AiEngineHealthClient {

    private static final Logger log = LoggerFactory.getLogger(AiEngineHealthClient.class);
    private static final String HEALTH_PATH = "/health";
    private static final String HEALTHY = "ok";

    private final RestClient healthClient;
    private final String host;

    public AiEngineHealthClient(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(aiProperties.wake().attemptTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.host = aiProperties.engine().host();
        this.healthClient = RestClient.builder()
            .baseUrl(aiProperties.engine().baseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    /**
     * Listo solo si responde el JSON del motor ({"status":"ok"}): mientras despierta, Render puede
     * devolver su propia página HTML con un 200, y eso no significa que el motor ya arrancó.
     */
    public boolean isReady() {
        long start = System.nanoTime();
        try {
            HealthResponse health = healthClient.get().uri(HEALTH_PATH)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(HealthResponse.class);
            boolean ready = health != null && HEALTHY.equals(health.status());
            if (!ready) log.warn("AI engine health check answered without status ok: host={}, elapsedMs={}", host, AiEngineCallLog.elapsedMs(start));
            return ready;
        } catch (RestClientException ex) {
            AiEngineCallLog.warnFailure(log, "health check", host, ex, AiEngineCallLog.elapsedMs(start));
            return false;
        } catch (IllegalArgumentException ex) {
            // URL base inválida: ya avisé al arrancar, aquí solo digo que no está listo.
            log.warn("AI engine health check skipped: invalid base URL");
            return false;
        }
    }

    private record HealthResponse(String status) {
    }
}
