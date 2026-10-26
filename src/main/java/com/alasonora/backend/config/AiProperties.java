package com.alasonora.backend.config;

import java.net.URI;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Agrupo ai.* en un record tipado para que spring-boot-configuration-processor genere sus metadatos. */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(Engine engine, Executor executor, Catalog catalog, Photo photo, Wake wake) {

    /**
     * Datos de conexión al motor bioacústico Python/FastAPI. El reintento solo ocurre ante
     * fallos rápidos y si aún cabe en el tiempo de la petición asíncrona.
     */
    public record Engine(
        String baseUrl,
        String apiKey,
        long timeoutMs,
        double minConfidence,
        int maxResults,
        long audioFetchTimeoutMs,
        int retryAttempts,
        long retryBackoffMs,
        long asyncSafetyMarginMs
    ) {

        private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

        // Quito espacios y barras finales: así nunca armo "//health" ni "//analyze".
        public Engine {
            baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        }

        /** Host del motor para los logs, o null si la URL no es http(s) absoluta. */
        public String host() {
            try {
                URI uri = URI.create(baseUrl);
                return uri.getScheme() != null && ALLOWED_SCHEMES.contains(uri.getScheme()) ? uri.getHost() : null;
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    /** Umbrales de clasificación de fotos; reutiliza la conexión del motor (baseUrl/apiKey/timeoutMs) porque es el mismo servicio Python, otro endpoint. */
    public record Photo(double minConfidence, int maxResults) {
    }

    /** Tamaño del pool bulkhead que aísla el trabajo de IA de los hilos de Tomcat. */
    public record Executor(int corePoolSize, int maxPoolSize, int queueCapacity) {
    }

    /** Aviso científico que acompaña a cada clasificación de IA. */
    public record Catalog(String disclaimer) {
    }

    /**
     * Despertar del motor en segundo plano: cada intento espera hasta attemptTimeoutMs mientras Render
     * lo arranca, reintento cada retryBackoffMs hasta maxDurationMs, y el "listo" vale readyFreshMs.
     */
    public record Wake(long attemptTimeoutMs, long maxDurationMs, long retryBackoffMs, long readyFreshMs) {
    }
}
