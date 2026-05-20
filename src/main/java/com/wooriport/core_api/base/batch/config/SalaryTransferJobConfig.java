package com.wooriport.core_api.base.batch.config;

import com.wooriport.core_api.base.batch.tasklet.RebalancingTasklet;
import com.wooriport.core_api.base.batch.tasklet.SalaryTransferTasklet;
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
public class SalaryTransferJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SalaryTransferTasklet salaryTransferTasklet;
    private final RebalancingTasklet rebalancingTasklet;

    @Bean
    public Job salaryTransferJob() {
        return new JobBuilder("salaryTransferJob", jobRepository)
                .start(salaryTransferStep())
                .next(rebalancingStep())
                .build();
    }

    @Bean
    public Step salaryTransferStep() {
        return new StepBuilder("salaryTransferStep", jobRepository)
                .tasklet(salaryTransferTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step rebalancingStep() {
        return new StepBuilder("rebalancingStep", jobRepository)
                .tasklet(rebalancingTasklet, transactionManager)
                .build();
    }
}