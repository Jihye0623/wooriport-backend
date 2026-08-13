package com.wooriport.core_api.batch;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.AssetSnapshotsRepository;
import com.wooriport.core_api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배치 Job이 실제로 Spring Batch 프레임워크 위에서 올바르게 배선/실행되는지 확인하는 통합테스트.
 *
 * tasklet 단위테스트(RebalancingTaskletTest, SalaryTransferTaskletTest 등)는 execute()를
 * Spring Batch 프레임워크 밖에서 직접 호출하는 것이라, Job/Step이 실제로 올바르게 연결됐는지
 * (Step 실행 순서, ExitStatus, 청크 파이프라인 reader→processor→writer 동작)는 검증하지 못했다.
 * JobLauncherTestUtils로 실제 Job을 launch시켜 그 갭을 메운다.
 *
 * salaryTransferJob/monthlyReportJob은 오늘 날짜에 해당하는 대상자·활성 유저를 시드하지 않아
 * (findBySalaryDate/findAllActiveUsers가 빈 리스트를 반환) tasklet 내부 로직이 사실상 no-op으로
 * 끝난다 — 이 테스트의 목적은 비즈니스 로직이 아니라 "Job이 COMPLETED로 끝나고 Step이 설정한
 * 순서대로 실행되는가"이므로, 외부 API(Flask 등)를 호출하는 실제 데이터 경로는 의도적으로 피했다.
 * assetSnapshotJob만 청크 파이프라인 자체를 검증하기 위해 실제 유저를 시드해 리더→프로세서→라이터가
 * 끝까지 동작하는지 확인한다.
 */
@SpringBootTest
@SpringBatchTest
@Testcontainers
class BatchJobIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier("salaryTransferJob") Job salaryTransferJob;
    @Autowired @Qualifier("monthlyReportJob") Job monthlyReportJob;
    @Autowired @Qualifier("assetSnapshotJob") Job assetSnapshotJob;

    @Autowired UserRepository userRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired AssetSnapshotsRepository assetSnapshotsRepository;

    @Test
    @DisplayName("salaryTransferJob: salaryTransferStep -> rebalancingStep 순서로 실행되어 COMPLETED로 끝난다")
    void salaryTransferJob_runsStepsInOrder_completesSuccessfully() throws Exception {
        jobLauncherTestUtils.setJob(salaryTransferJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueParams());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<String> stepNames = execution.getStepExecutions().stream()
                .map(StepExecution::getStepName)
                .toList();
        assertThat(stepNames).containsExactly("salaryTransferStep", "rebalancingStep");
    }

    @Test
    @DisplayName("monthlyReportJob: monthlyReportStep이 실행되어 COMPLETED로 끝난다")
    void monthlyReportJob_completesSuccessfully() throws Exception {
        jobLauncherTestUtils.setJob(monthlyReportJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueParams());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly("monthlyReportStep");
    }

    @Test
    @DisplayName("assetSnapshotJob: 활성 유저를 청크(reader→processor→writer)로 처리해 스냅샷을 실제로 저장한다")
    void assetSnapshotJob_processesActiveUsers_persistsSnapshots() throws Exception {
        Users user = userRepository.save(Users.builder()
                .password("pw").email("batch-job@test.com").name("홍길동").build());
        assetRepository.save(Assets.builder()
                .user(user).institution("우리은행")
                .assetType(Assets.AccountType.CHECKING).balance(500_000L)
                .bankType(Assets.BankType.WOORI).syncedAt(LocalDateTime.now()).build());

        jobLauncherTestUtils.setJob(assetSnapshotJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueParams());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(assetSnapshotsRepository.findAll())
                .anyMatch(s -> s.getUser().getId().equals(user.getId()) && s.getSavingsAmount() == 500_000L);
    }

    private JobParameters uniqueParams() {
        return new JobParametersBuilder()
                .addLong("uniqueId", System.nanoTime())
                .toJobParameters();
    }
}
