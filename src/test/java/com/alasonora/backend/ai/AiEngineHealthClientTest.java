package com.alasonora.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.alasonora.backend.ai.AiEngineHealthClient.Health;
import com.alasonora.backend.config.AiProperties;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** La página de "despertando" de Render (HTML con 200) no cuenta como motor listo, y cada fallo queda en el log. */
class AiEngineHealthClientTest {

    private static final String API_KEY = "secret-test-key";

    private HttpServer server;
    private final Logger logger = (Logger) LoggerFactory.getLogger(AiEngineHealthClient.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        logger.detachAppender(logs);
    }

    @Test
    void readyOnlyWithEngineJson() throws IOException {
        assertEquals(Health.READY, clientServing(200, "application/json", "{\"status\":\"ok\"}").check());
    }

    @Test
    void renderWakingPageIsNotReady() throws IOException {
        assertEquals(Health.FAILED, clientServing(200, "text/html", "<html><body>Service waking up…</body></html>").check());
    }

    @Test
    void serverErrorIsNotReady() throws IOException {
        assertEquals(Health.FAILED, clientServing(503, "text/plain", "Service Unavailable").check());
    }

    @Test
    void renderThrottlingIsAsleepAndNotWarned() throws IOException {
        captureLogs();
        assertEquals(Health.ASLEEP, clientServing(429, "text/plain", "Too Many Requests").check());
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void failureIsLoggedWithHostAndStatusButNoSecrets() throws IOException {
        captureLogs();
        clientServing(503, "text/plain", "Service Unavailable " + API_KEY).check();

        List<String> messages = new ArrayList<>();
        for (ILoggingEvent event : logs.list) {
            messages.add(event.getFormattedMessage());
        }
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("host=127.0.0.1"), messages.get(0));
        assertTrue(messages.get(0).contains("status=503"), messages.get(0));
        assertTrue(messages.get(0).contains("elapsedMs="), messages.get(0));
        assertFalse(messages.get(0).contains(API_KEY), messages.get(0));
    }

    @Test
    void invalidBaseUrlIsNotReadyAndDoesNotThrow() {
        assertEquals(Health.FAILED, new AiEngineHealthClient(properties("not a url")).check());
    }

    private void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    private AiEngineHealthClient clientServing(int status, String contentType, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        // La barra final de la URL no debe terminar en "//health", que el motor responde con 404.
        return new AiEngineHealthClient(properties("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    static AiProperties properties(String baseUrl) {
        AiProperties.Engine engine = new AiProperties.Engine(baseUrl, API_KEY, 2000, 0.1, 3, 1000, 1, 10, 1000);
        return new AiProperties(engine, null, null, null, new AiProperties.Wake(1000, 1000, 10, 60_000));
    }
}
