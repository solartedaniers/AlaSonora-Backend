package com.alasonora.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alasonora.backend.config.AiProperties;
import com.sun.net.httpserver.HttpServer;

/** Un solo despertar en curso, que reintenta hasta que el motor responde o se agota el tiempo total. */
class AiEngineWakeUpServiceTest {

    private HttpServer server;
    private final AtomicInteger healthCalls = new AtomicInteger();
    private volatile int failuresBeforeReady;
    // Guardo las tareas en vez de correrlas: así controlo cuándo corre el despertar.
    private final List<Runnable> queued = new ArrayList<>();
    private AiEngineReadiness readiness;

    @BeforeEach
    void startStubEngine() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            boolean asleep = healthCalls.incrementAndGet() <= failuresBeforeReady;
            byte[] body = (asleep ? "Service Unavailable" : "{\"status\":\"ok\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(asleep ? 503 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopStubEngine() {
        server.stop(0);
    }

    @Test
    void launchesASingleWakeUpWhileOneIsInProgress() {
        AiEngineWakeUpService service = service(60_000);

        assertTrue(service.wakeUp());
        assertFalse(service.wakeUp());
        assertEquals(1, queued.size());

        queued.get(0).run();
        assertTrue(service.wakeUp(), "Once finished, a new wake-up can start");
    }

    @Test
    void retriesUntilTheEngineAnswersAndMarksItReady() {
        failuresBeforeReady = 2;
        AiEngineWakeUpService service = service(60_000);

        service.wakeUp();
        queued.get(0).run();

        assertEquals(3, healthCalls.get());
        assertTrue(readiness.isReady());
    }

    @Test
    void givesUpAfterTheMaxDuration() {
        failuresBeforeReady = Integer.MAX_VALUE;
        AiEngineWakeUpService service = service(100);

        service.wakeUp();
        queued.get(0).run();

        assertFalse(readiness.isReady());
        assertTrue(healthCalls.get() >= 1);
    }

    private AiEngineWakeUpService service(long maxDurationMs) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties.Engine engine = new AiProperties.Engine(base, "key", 2000, 0.1, 3, 1000, 1, 10, 1000);
        AiProperties properties = new AiProperties(engine, null, null, null, new AiProperties.Wake(1000, maxDurationMs, 10, 60_000));
        readiness = new AiEngineReadiness(Clock.systemUTC(), properties);
        return new AiEngineWakeUpService(new AiEngineHealthClient(properties), readiness, queued::add, properties);
    }
}
