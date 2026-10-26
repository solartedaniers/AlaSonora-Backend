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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.alasonora.backend.config.AiProperties;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Un solo despertar en curso, que reintenta hasta que el motor responde o se agota el tiempo total. */
class AiEngineWakeUpServiceTest {

    private HttpServer server;
    private final AtomicInteger healthCalls = new AtomicInteger();
    private volatile int failuresBeforeReady;
    private volatile int asleepStatus = 503;
    // Guardo las tareas en vez de correrlas: así controlo cuándo corre el despertar.
    private final List<Runnable> queued = new ArrayList<>();
    private AiEngineReadiness readiness;
    private final List<Runnable> attached = new ArrayList<>();

    @BeforeEach
    void startStubEngine() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            boolean asleep = healthCalls.incrementAndGet() <= failuresBeforeReady;
            byte[] body = (asleep ? "Service Unavailable" : "{\"status\":\"ok\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(asleep ? asleepStatus : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopStubEngine() {
        server.stop(0);
        for (Runnable task : attached) {
            task.run();
        }
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

    @Test
    void logsRenderThrottlingOncePerWakeUpCycle() {
        failuresBeforeReady = 5;
        asleepStatus = 429;
        AiEngineWakeUpService service = service(60_000);
        ListAppender<ILoggingEvent> logs = captureLogs(AiEngineWakeUpService.class, AiEngineHealthClient.class);

        service.wakeUp();
        queued.get(0).run();
        service.wakeUp();
        queued.get(1).run();

        List<ILoggingEvent> asleep = new ArrayList<>();
        int warnings = 0;
        for (ILoggingEvent event : logs.list) {
            if (event.getFormattedMessage().contains("woken by client traffic")) asleep.add(event);
            if (event.getLevel() == Level.WARN) warnings++;
        }
        assertEquals(1, asleep.size(), "Only the first cycle meets the sleeping engine");
        assertEquals(Level.INFO, asleep.get(0).getLevel());
        assertEquals(0, warnings);
    }

    @Test
    void skipsTheWakeUpWithoutThrowingWhenTheAiPoolIsFull() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties.Engine engine = new AiProperties.Engine(base, "key", 2000, 0.1, 3, 1000, 1, 10, 1000);
        AiProperties properties = new AiProperties(engine, null, null, null, new AiProperties.Wake(1000, 60_000, 10, 60_000));
        Executor full = task -> {
            throw new RejectedExecutionException("full");
        };
        AiEngineWakeUpService service = new AiEngineWakeUpService(new AiEngineHealthClient(properties), new AiEngineReadiness(Clock.systemUTC(), properties), full, properties);

        assertFalse(service.wakeUp());
        assertFalse(service.wakeUp(), "Still full: it never runs on the caller thread");
        assertEquals(0, healthCalls.get());
    }

    private ListAppender<ILoggingEvent> captureLogs(Class<?>... sources) {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        for (Class<?> source : sources) {
            Logger logger = (Logger) LoggerFactory.getLogger(source);
            logger.addAppender(logs);
            attached.add(() -> logger.detachAppender(logs));
        }
        return logs;
    }

    private AiEngineWakeUpService service(long maxDurationMs) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties.Engine engine = new AiProperties.Engine(base, "key", 2000, 0.1, 3, 1000, 1, 10, 1000);
        AiProperties properties = new AiProperties(engine, null, null, null, new AiProperties.Wake(1000, maxDurationMs, 10, 60_000));
        readiness = new AiEngineReadiness(Clock.systemUTC(), properties);
        return new AiEngineWakeUpService(new AiEngineHealthClient(properties), readiness, new QueueingExecutor(), properties);
    }

    // Guardo la tarea en la cola en vez de correrla.
    private final class QueueingExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }
    }
}
