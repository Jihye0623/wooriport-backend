package com.wooriport.core_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // @EnableAsync 추가만으로 @Async 동작
    // 별도 Executor 설정 없으면 Spring 기본 ThreadPoolTaskExecutor 사용
}