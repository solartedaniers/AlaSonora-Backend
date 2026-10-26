package com.alasonora.backend.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.alasonora.backend.exception.ApiErrorCode;
import com.alasonora.backend.exception.ApiErrorMessages;
import com.alasonora.backend.exception.CodedResponseStatusException;

/** Lanzo el trabajo de IA en su pool; si el pool y la cola están llenos respondo 503 en vez de usar el hilo de Tomcat. */
@Component
public class AiTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(AiTaskRunner.class);

    private final Executor aiTaskExecutor;

    public AiTaskRunner(@Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.aiTaskExecutor = aiTaskExecutor;
    }

    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, aiTaskExecutor);
        } catch (RejectedExecutionException rejected) {
            log.warn("AI task rejected: the AI pool and its queue are full");
            throw new CodedResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorMessages.AI_ENGINE_BUSY, ApiErrorCode.AI_ENGINE_BUSY);
        }
    }
}
