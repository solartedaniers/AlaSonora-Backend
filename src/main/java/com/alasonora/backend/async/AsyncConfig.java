package com.alasonora.backend.async;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.alasonora.backend.config.AiProperties;

/** Corro la IA en su propio pool acotado para que un motor lento nunca deje sin hilos a Tomcat. */
@Configuration
@EnableAsync
public class AsyncConfig {

    private final AiProperties aiProperties;

    public AsyncConfig(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Bean
    Executor aiTaskExecutor() {
        AiProperties.Executor config = aiProperties.executor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.corePoolSize());
        executor.setMaxPoolSize(config.maxPoolSize());
        executor.setQueueCapacity(config.queueCapacity());
        executor.setThreadNamePrefix("ai-task-");
        // Con el pool y su cola llenos rechazo la tarea: nunca corre en el hilo de Tomcat (AiTaskRunner responde 503).
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
