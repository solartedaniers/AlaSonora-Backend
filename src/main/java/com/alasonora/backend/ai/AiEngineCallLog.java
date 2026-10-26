package com.alasonora.backend.ai;

import org.slf4j.Logger;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;

/**
 * Formato único del WARN cuando falla una llamada al motor. Registro solo el tipo de error, el host,
 * el status y el tiempo: nunca el mensaje de la excepción, que puede traer el cuerpo de la respuesta.
 */
final class AiEngineCallLog {

    private static final String NO_STATUS = "none";

    private AiEngineCallLog() {
    }

    static void warnFailure(Logger log, String operation, String host, RestClientException ex, long elapsedMs) {
        log.warn("AI engine {} failed: error={}, host={}, status={}, elapsedMs={}",
            operation, ex.getClass().getSimpleName(), host, statusOf(ex), elapsedMs);
    }

    static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String statusOf(RestClientException ex) {
        if (ex instanceof RestClientResponseException response) return String.valueOf(response.getStatusCode().value());
        if (ex instanceof UnknownContentTypeException unknown) return String.valueOf(unknown.getStatusCode().value());
        return NO_STATUS;
    }
}
