package com.wooriport.core_api.base.batch.config;


import com.wooriport.core_api.base.batch.tasklet.MonthlyReportTasklet;
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
public class MonthlyReportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MonthlyReportTasklet monthlyReportTasklet;

    @Bean
    public Job monthlyReportJob() {
        return new JobBuilder("monthlyReportJob", jobRepository)
                .start(monthlyReportStep())
                .build();
    }

    @Bean
    public Step monthlyReportStep() {
        return new StepBuilder("monthlyReportStep", jobRepository)
                .tasklet(monthlyReportTasklet, transactionManager)
                .build();
    }
}