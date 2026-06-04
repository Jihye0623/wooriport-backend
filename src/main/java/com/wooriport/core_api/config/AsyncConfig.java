package com.wooriport.core_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);    // 평상시 유지할 스레드 수
        executor.setMaxPoolSize(20);    // 최대 스레드 수
        executor.setQueueCapacity(100); // 큐 용량 (max 초과 시 여기서 대기)
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}