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
public class SalaryTransferScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("salaryTransferJob")
    private final Job salaryTransferJob;

    /**
     * 매일 00:00 실행
     *
     * 테스트 시 주기 변경:
     * "0 * * * * *"  → 매분 실행
     * "0 0 0 * * *"  → 매일 자정 (운영)
     */
    @Scheduled(cron = "0 * * * * *")
    public void run() {
        try {
            // executedAt 파라미터로 매일 새 Job 실행 보장
            // (동일 파라미터면 Spring Batch가 중복 실행 방지)
            JobParameters params = new JobParametersBuilder()
                    .addString("executedAt", LocalDateTime.now().toString())
                    .toJobParameters();

            log.info("[SalaryTransferScheduler] Job 시작 — {}", LocalDateTime.now());
            jobLauncher.run(salaryTransferJob, params);

        } catch (Exception e) {
            log.error("[SalaryTransferScheduler] Job 실행 실패: {}", e.getMessage(), e);
        }
    }
}