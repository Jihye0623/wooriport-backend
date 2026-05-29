package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.AssetSnapshots;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.AssetSnapshotsRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSnapshotTasklet implements Tasklet {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetSnapshotsRepository assetSnapshotsRepository;

    private static final Set<Assets.AccountType> SAVINGS_TYPES = EnumSet.of(
            Assets.AccountType.CHECKING,
            Assets.AccountType.PARKING,
            Assets.AccountType.SAVINGS,
            Assets.AccountType.DEPOSIT,
            Assets.AccountType.CMA
    );

    private static final Set<Assets.AccountType> INVEST_TYPES = EnumSet.of(
            Assets.AccountType.STOCK,
            Assets.AccountType.IRP,
            Assets.AccountType.ISA
    );

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        LocalDateTime snapshotAt = LocalDateTime.now();
        log.info("[AssetSnapshotJob] 실행 — {}", snapshotAt);

        List<Users> users = userRepository.findAllActiveUsers();

        if (users.isEmpty()) {
            log.info("[AssetSnapshotJob] 활성 사용자 없음");
            return RepeatStatus.FINISHED;
        }

        log.info("[AssetSnapshotJob] 대상 사용자 {}명", users.size());

        for (Users user : users) {
            try {
                List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(user.getId());

                long totalAmount   = 0L;
                long savingsAmount = 0L;
                long investAmount  = 0L;

                for (Assets asset : assets) {
                    long balance = asset.getBalance() != null ? asset.getBalance() : 0L;
                    totalAmount += balance;

                    if (asset.getAssetType() != null) {
                        if (SAVINGS_TYPES.contains(asset.getAssetType())) {
                            savingsAmount += balance;
                        } else if (INVEST_TYPES.contains(asset.getAssetType())) {
                            investAmount += balance;
                        }
                    }
                }

                assetSnapshotsRepository.save(AssetSnapshots.builder()
                        .user(user)
                        .snapshotAt(snapshotAt)
                        .totalAmount(totalAmount)
                        .savingsAmount(savingsAmount)
                        .investAmount(investAmount)
                        .build());

                log.info("[AssetSnapshotJob] 완료 — userId: {}, total: {}원", user.getId(), totalAmount);

            } catch (Exception e) {
                log.error("[AssetSnapshotJob] 실패 — userId: {}, 사유: {}", user.getId(), e.getMessage());
            }
        }

        log.info("[AssetSnapshotJob] 전체 완료");
        return RepeatStatus.FINISHED;
    }
}
