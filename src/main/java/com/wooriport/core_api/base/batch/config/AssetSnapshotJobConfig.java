package com.wooriport.core_api.base.batch.config;

import com.wooriport.core_api.base.batch.tasklet.AssetSnapshotTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class AssetSnapshotJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AssetSnapshotTasklet assetSnapshotTasklet;

    @Bean
    public Job assetSnapshotJob() {
        return new JobBuilder("assetSnapshotJob", jobRepository)
                .start(assetSnapshotStep())
                .build();
    }

    @Bean
    public Step assetSnapshotStep() {
        return new StepBuilder("assetSnapshotStep", jobRepository)
                .tasklet(assetSnapshotTasklet, transactionManager)
                .build();
    }
}
