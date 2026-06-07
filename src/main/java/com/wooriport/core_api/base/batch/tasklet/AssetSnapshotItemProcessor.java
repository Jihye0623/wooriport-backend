package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.AssetSnapshots;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@StepScope  // step 시작 시 1회 생성 → snapshotAt이 잡 전체에서 동일, 싱글톤 주입 시 프록시 사용
@RequiredArgsConstructor
public class AssetSnapshotItemProcessor implements ItemProcessor<Users, AssetSnapshots> {

    private final AssetRepository assetRepository;

    private static final Set<Assets.AccountType> SAVINGS_TYPES = EnumSet.of(
            Assets.AccountType.CHECKING, Assets.AccountType.PARKING,
            Assets.AccountType.SAVINGS, Assets.AccountType.DEPOSIT, Assets.AccountType.CMA
    );
    private static final Set<Assets.AccountType> INVEST_TYPES = EnumSet.of(
            Assets.AccountType.STOCK, Assets.AccountType.IRP, Assets.AccountType.ISA
    );

    // step 생성 시점에 고정 — 같은 잡 실행 내 모든 레코드가 동일한 시각을 가짐
    private final LocalDateTime snapshotAt = LocalDateTime.now();

    @Override
    public AssetSnapshots process(Users user) {
        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(user.getId());

        long total = 0L, savings = 0L, invest = 0L;
        for (Assets asset : assets) {
            long balance = asset.getBalance() != null ? asset.getBalance() : 0L;
            total += balance;
            if (asset.getAssetType() != null) {
                if (SAVINGS_TYPES.contains(asset.getAssetType())) savings += balance;
                else if (INVEST_TYPES.contains(asset.getAssetType())) invest += balance;
            }
        }

        log.info("[AssetSnapshotJob] 처리 완료 — userId: {}, total: {}원", user.getId(), total);

        return AssetSnapshots.builder()
                .user(user)
                .snapshotAt(snapshotAt)
                .totalAmount(total)
                .savingsAmount(savings)
                .investAmount(invest)
                .build();
    }
}
