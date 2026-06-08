package com.wooriport.core_api.base.batch.config;

import com.wooriport.core_api.base.batch.tasklet.AssetSnapshotItemProcessor;
import com.wooriport.core_api.base.batch.tasklet.AssetSnapshotItemWriter;
import com.wooriport.core_api.domain.AssetSnapshots;
import com.wooriport.core_api.domain.Users;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class AssetSnapshotJobConfig {

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final AssetSnapshotItemProcessor processor;
    private final AssetSnapshotItemWriter writer;

    @Bean
    public Job assetSnapshotJob() {
        return new JobBuilder("assetSnapshotJob", jobRepository)
                .start(assetSnapshotStep())
                .build();
    }

    @Bean
    public Step assetSnapshotStep() {
        return new StepBuilder("assetSnapshotStep", jobRepository)
                .<Users, AssetSnapshots>chunk(CHUNK_SIZE, transactionManager)
                .reader(assetSnapshotItemReader())
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(Exception.class)   // 개별 유저 처리 실패 시 스킵
                .skipLimit(10)           // 10명 초과 실패 시 잡 중단
                .build();
    }

    @Bean
    public JpaPagingItemReader<Users> assetSnapshotItemReader() {
        return new JpaPagingItemReaderBuilder<Users>()
                .name("assetSnapshotItemReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        SELECT u FROM Users u
                        WHERE u.status = 'ACTIVE'
                          AND u.deletedAt IS NULL
                        """)
                .pageSize(CHUNK_SIZE)
                .build();
    }
}
