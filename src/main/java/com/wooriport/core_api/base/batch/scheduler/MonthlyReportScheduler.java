package com.wooriport.core_api.base.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("monthlyReportJob")
    private final Job monthlyReportJob;

    /**
     * 매월 1일 00:00 실행
     * 테스트 시: "0 * * * * *" (매분)
     * 운영 시:   "0 0 0 1 * *" (매월 1일 자정)
     */
    @Scheduled(cron = "0 * * * * *")
    public void run() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("executedAt", LocalDateTime.now().toString())
                    .toJobParameters();

            log.info("[MonthlyReportScheduler] Job 시작 — {}", LocalDateTime.now());
            jobLauncher.run(monthlyReportJob, params);

        } catch (Exception e) {
            log.error("[MonthlyReportScheduler] Job 실행 실패: {}", e.getMessage(), e);
        }
    }
}