package com.alasonora.backend.ai;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.alasonora.backend.config.AiProperties;

/**
 * Consulto el /health del motor desde AiEngineWakeUpService, en segundo plano: el timeout largo
 * deja que Render retenga la petición mientras arranca.
 */
@Component
public class AiEngineHealthClient {

    private static final Logger log = LoggerFactory.getLogger(AiEngineHealthClient.class);
    private static final String HEALTH_PATH = "/health";
    private static final String HEALTHY = "ok";

    /** Resultado de una consulta; ASLEEP es el 429 con que Render frena el tráfico al servicio dormido. */
    public enum Health { READY, ASLEEP, FAILED }

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
     * Lo doy por listo solo con el JSON del motor: mientras despierta, Render puede responder su página HTML con 200.
     * El 429 de Render lo cuenta una sola vez AiEngineWakeUpService.
     */
    public Health check() {
        long start = System.nanoTime();
        try {
            HealthResponse health = healthClient.get().uri(HEALTH_PATH)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(HealthResponse.class);
            if (health != null && HEALTHY.equals(health.status())) return Health.READY;
            log.warn("AI engine health check answered without status ok: host={}, elapsedMs={}", host, AiEngineCallLog.elapsedMs(start));
            return Health.FAILED;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) return Health.ASLEEP;
            AiEngineCallLog.warnFailure(log, "health check", host, ex, AiEngineCallLog.elapsedMs(start));
            return Health.FAILED;
        } catch (RestClientException ex) {
            AiEngineCallLog.warnFailure(log, "health check", host, ex, AiEngineCallLog.elapsedMs(start));
            return Health.FAILED;
        } catch (IllegalArgumentException ex) {
            // URL base inválida: ya avisé al arrancar, aquí solo digo que no está listo.
            log.warn("AI engine health check skipped: invalid base URL");
            return Health.FAILED;
        }
    }

    private record HealthResponse(String status) {
    }
}
