package com.wooriport.core_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

/**
 * Phase 2: transaction-events 전용 배치 컨슈머 팩토리.
 * Phase 3: concurrency(병렬 컨슈머 수) + 파티션 수를 application.yml 에서 조정 가능.
 * SalaryRetryConsumer 는 기존 단건 기본 팩토리를 그대로 사용.
 */
@Configuration
public class KafkaBatchConfig {

    // Phase 3 sweep: 컨슈머 스레드 수 (≤ 파티션 수)
    @Value("${wooriport.kafka.consumer-concurrency:1}")
    private int concurrency;

    // Phase 3 sweep: transaction-events 파티션 수 (증가만 가능; 감소는 토픽 삭제 후 재기동)
    @Value("${wooriport.kafka.partitions:3}")
    private int partitions;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(concurrency);
        return factory;
    }

    // 앱 기동 시 토픽 파티션 수 적용 (이미 존재하면 증가만; 감소 불가)
    @Bean
    public NewTopics transactionEventsTopic() {
        return new NewTopics(
                TopicBuilder.name("transaction-events")
                        .partitions(partitions)
                        .replicas(1)
                        .build()
        );
    }
}
