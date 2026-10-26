package com.alasonora.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.alasonora.backend.config.AiProperties;
import com.alasonora.backend.config.AudioUploadProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import tools.jackson.databind.json.JsonMapper;

/** Reintento corto ante el 502 del proxy de Render, error estable cuando el motor no despierta y log sin la clave. */
class BirdNetHttpClassifierRetryTest {

    private static final String ANALYZE_OK = "{\"candidates\":[{\"scientific_name\":\"Cyanocitta cristata\",\"common_name\":\"Blue Jay\",\"confidence\":0.9}]}";
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(30);
    private static final String API_KEY = "secret-test-key";

    private HttpServer server;
    private final AtomicInteger analyzeCalls = new AtomicInteger();
    private volatile int failuresBeforeSuccess;
    private final Logger logger = (Logger) LoggerFactory.getLogger(BirdNetHttpClassifier.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void startStubEngine() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio.wav", exchange -> respond(exchange, 200, "RIFF\0\0\0\0WAVEfmt "));
        server.createContext("/analyze", exchange -> {
            boolean fail = analyzeCalls.incrementAndGet() <= failuresBeforeSuccess;
            respond(exchange, fail ? 502 : 200, fail ? "Bad Gateway" : ANALYZE_OK);
        });
        server.start();
    }

    @AfterEach
    void stopStubEngine() {
        server.stop(0);
        logger.detachAppender(logs);
    }

    @Test
    void retriesOnceAfterTransient502() {
        failuresBeforeSuccess = 1;
        List<RawClassificationCandidate> result = classifier().classify(request());
        assertEquals(2, analyzeCalls.get());
        assertEquals("Cyanocitta cristata", result.get(0).scientificName());
    }

    @Test
    void reportsEngineUnavailableWhenItStaysDown() {
        failuresBeforeSuccess = Integer.MAX_VALUE;
        assertThrows(AiEngineUnavailableException.class, () -> classifier().classify(request()));
        assertEquals(2, analyzeCalls.get());
    }

    @Test
    void logsEachFailureWithHostAndStatusButNoApiKey() {
        failuresBeforeSuccess = Integer.MAX_VALUE;
        logs.start();
        logger.addAppender(logs);

        assertThrows(AiEngineUnavailableException.class, () -> classifier().classify(request()));

        List<String> messages = new ArrayList<>();
        for (ILoggingEvent event : logs.list) {
            messages.add(event.getFormattedMessage());
        }
        assertEquals(2, messages.size());
        for (String message : messages) {
            assertTrue(message.contains("host=127.0.0.1") && message.contains("status=502"), message);
            assertFalse(message.contains(API_KEY), message);
        }
    }

    private BirdNetHttpClassifier classifier() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties.Engine engine = new AiProperties.Engine(base, API_KEY, 2000, 0.1, 3, 1000, 1, 10, 1000);
        AudioFileValidator validator = new AudioFileValidator(new AudioUploadProperties(1_048_576));
        return new BirdNetHttpClassifier(new AiProperties(engine, null, null, null, null), JsonMapper.builder().build(), validator, ASYNC_TIMEOUT);
    }

    private ClassificationRequest request() {
        String audioUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/audio.wav";
        return new ClassificationRequest(audioUrl, null, null, null);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
