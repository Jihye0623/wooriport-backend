package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
@Slf4j
@Component
@RequiredArgsConstructor
public class SalaryTransferTasklet implements Tasklet {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        int today = LocalDate.now().getDayOfMonth();

        log.info("[AutoTransferJob] 실행 — {}일", today);

        List<Users> targets = userRepository.findBySalaryDate(today);

        if (targets.isEmpty()) {
            log.info("[AutoTransferJob] 오늘 자동이체 대상 없음");
            return RepeatStatus.FINISHED;
        }

        for (Users user : targets) {
            try {
                // 1. 타행 급여 통장 조회 (isSalary = true)
                Assets fromAsset = assetRepository
                        .findByUserIdAndIsSalaryTrue(user.getId())
                        .orElseThrow(() -> new IllegalStateException("급여 통장 미설정"));

                // 2. 우리은행 이체 대상 계좌 조회
                if (user.getAutoTransferToAssetId() == null) {
                    log.warn("[AutoTransferJob] 이체 대상 계좌 미설정 — userId: {}", user.getId());
                    continue;
                }

                Assets toAsset = assetRepository.findById(user.getAutoTransferToAssetId())
                        .orElseThrow(() -> new IllegalStateException("이체 대상 계좌 없음"));

                // 3. 타행 잔액 전액 우리은행으로 이체
                Long transferAmount = fromAsset.getBalance();

                if (transferAmount <= 0) {
                    log.warn("[AutoTransferJob] 급여 통장 잔액 없음 — userId: {}", user.getId());
                    continue;
                }

                fromAsset.updateBalance(0L);                               // 타행 출금
                toAsset.updateBalance(toAsset.getBalance() + transferAmount); // 우리은행 입금

                log.info("[AutoTransferJob] 완료 — userId: {}, {}원 이체", user.getId(), transferAmount);

            } catch (Exception e) {
                log.error("[AutoTransferJob] 실패 — userId: {}, 사유: {}", user.getId(), e.getMessage());
            }
        }

        return RepeatStatus.FINISHED;
    }
}