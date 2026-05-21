package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
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
public class RebalancingTasklet implements Tasklet {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        int today = LocalDate.now().getDayOfMonth();
        log.info("[Step2 Rebalancing] 실행 — {}일", today);

        List<Users> targets = userRepository.findBySalaryDate(today);

        if (targets.isEmpty()) {
            log.info("[Step2 Rebalancing] 오늘 리밸런싱 대상 없음");
            return RepeatStatus.FINISHED;
        }

        for (Users user : targets) {
            try {
                // 1. 리밸런싱 기준 계좌 = 우리은행 (auto_transfer_to_asset_id)
                if (user.getAutoTransferToAssetId() == null) {
                    log.warn("[Step2 Rebalancing] 우리은행 계좌 미설정 — userId: {}", user.getId());
                    continue;
                }

                Assets wooriAsset = assetRepository
                        .findById(user.getAutoTransferToAssetId())
                        .orElseThrow(() -> new IllegalStateException("우리은행 계좌 없음"));

                Long totalBalance = wooriAsset.getBalance();

                if (totalBalance <= 0) {
                    log.warn("[Step2 Rebalancing] 우리은행 잔액 없음 — userId: {}", user.getId());
                    continue;
                }

                // 2. portfolios 비율대로 각 계좌에 분배
                List<Portfolios> portfolios = portfolioRepository.findByUserId(user.getId());

                if (portfolios.isEmpty()) {
                    log.warn("[Step2 Rebalancing] 포트폴리오 미설정 — userId: {}", user.getId());
                    continue;
                }

                for (Portfolios portfolio : portfolios) {
                    // 계좌 미연동 항목 스킵
                    if (portfolio.getAsset() == null) {
                        log.info("[Step2 Rebalancing] 계좌 미연동 스킵 — type: {}",
                                portfolio.getAssetType());
                        continue;
                    }

                    // 우리은행 자기 자신은 스킵 (출발 계좌)
                    if (portfolio.getAsset().getId().equals(wooriAsset.getId())) {
                        continue;
                    }

                    Long amount = totalBalance * portfolio.getAssetRatio() / 100;

                    if (amount <= 0) continue;

                    // 우리은행에서 출금
                    wooriAsset.updateBalance(wooriAsset.getBalance() - amount);

                    // 목적 계좌에 입금
                    portfolio.getAsset().updateBalance(
                            portfolio.getAsset().getBalance() + amount);

                    log.info("[Step2 Rebalancing] 분배 — type: {}, {}원 → {}",
                            portfolio.getAssetType(),
                            amount,
                            portfolio.getAsset().getInstitution());
                }

                log.info("[Step2 Rebalancing] 완료 — userId: {}", user.getId());

            } catch (Exception e) {
                log.error("[Step2 Rebalancing] 실패 — userId: {}, 사유: {}",
                        user.getId(), e.getMessage());
            }
        }

        return RepeatStatus.FINISHED;
    }
}
