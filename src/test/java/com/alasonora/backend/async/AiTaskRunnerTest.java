package com.alasonora.backend.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.alasonora.backend.config.AiProperties;
import com.alasonora.backend.exception.ApiErrorCode;
import com.alasonora.backend.exception.CodedResponseStatusException;

/** Con el pool y la cola llenos respondo 503 AI_ENGINE_BUSY y la tarea nunca corre en el hilo que la pidió. */
class AiTaskRunnerTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void stop() {
        release.countDown();
        if (executor != null) executor.shutdown();
    }

    @Test
    void runsTasksOnTheAiPool() throws Exception {
        AiTaskRunner runner = new AiTaskRunner(pool(1, 1, 1));
        String caller = Thread.currentThread().getName();

        String worker = runner.supply(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);

        assertNotEquals(caller, worker);
        assertEquals(true, worker.startsWith("ai-task-"), worker);
    }

    @Test
    void answersBusyInsteadOfRunningOnTheCallerThreadWhenFull() {
        AiTaskRunner runner = new AiTaskRunner(pool(1, 1, 1));
        runner.supply(this::block);
        runner.supply(this::block);
        AtomicReference<String> ranOn = new AtomicReference<>();

        CodedResponseStatusException busy = assertThrows(CodedResponseStatusException.class,
            () -> runner.supply(() -> {
                ranOn.set(Thread.currentThread().getName());
                return null;
            }));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());
        assertEquals(ApiErrorCode.AI_ENGINE_BUSY, busy.getErrorCode());
        assertEquals(null, ranOn.get());
    }

    private Object block() {
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private Executor pool(int core, int max, int queue) {
        AiProperties properties = new AiProperties(null, new AiProperties.Executor(core, max, queue), null, null, null);
        executor = (ThreadPoolTaskExecutor) new AsyncConfig(properties).aiTaskExecutor();
        return executor;
    }
}
