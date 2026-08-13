package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.AssetSnapshots;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AssetSnapshotItemProcessorTest {

    @Mock AssetRepository assetRepository;
    @InjectMocks AssetSnapshotItemProcessor processor;

    @Test
    @DisplayName("저축형 계좌(입출금/파킹/적금/예금/CMA) 잔액은 savingsAmount로 합산된다")
    void process_savingsTypes_summedIntoSavingsAmount() {
        Users user = user();
        given(assetRepository.findByUserIdAndDeletedAtIsNull(user.getId())).willReturn(List.of(
                asset(Assets.AccountType.CHECKING, 100_000L),
                asset(Assets.AccountType.PARKING, 200_000L),
                asset(Assets.AccountType.SAVINGS, 300_000L),
                asset(Assets.AccountType.DEPOSIT, 400_000L),
                asset(Assets.AccountType.CMA, 500_000L)
        ));

        AssetSnapshots result = processor.process(user);

        assertThat(result.getSavingsAmount()).isEqualTo(1_500_000L);
        assertThat(result.getInvestAmount()).isZero();
        assertThat(result.getTotalAmount()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("투자형 계좌(증권/IRP/ISA) 잔액은 investAmount로 합산된다")
    void process_investTypes_summedIntoInvestAmount() {
        Users user = user();
        given(assetRepository.findByUserIdAndDeletedAtIsNull(user.getId())).willReturn(List.of(
                asset(Assets.AccountType.STOCK, 700_000L),
                asset(Assets.AccountType.IRP, 800_000L),
                asset(Assets.AccountType.ISA, 900_000L)
        ));

        AssetSnapshots result = processor.process(user);

        assertThat(result.getInvestAmount()).isEqualTo(2_400_000L);
        assertThat(result.getSavingsAmount()).isZero();
        assertThat(result.getTotalAmount()).isEqualTo(2_400_000L);
    }

    @Test
    @DisplayName("저축형·투자형 어디에도 속하지 않는 계좌(카드 등)는 totalAmount에는 포함되지만 savings/invest에는 반영되지 않는다")
    void process_neitherSavingsNorInvest_addsToTotalOnly() {
        Users user = user();
        given(assetRepository.findByUserIdAndDeletedAtIsNull(user.getId())).willReturn(List.of(
                asset(Assets.AccountType.CREDIT_CARD, -50_000L),
                asset(Assets.AccountType.HOUSING_SUBSCRIPTION, 1_000_000L)
        ));

        AssetSnapshots result = processor.process(user);

        assertThat(result.getTotalAmount()).isEqualTo(950_000L);
        assertThat(result.getSavingsAmount()).isZero();
        assertThat(result.getInvestAmount()).isZero();
    }

    @Test
    @DisplayName("잔액(balance)이 null인 자산은 0원으로 취급되어 예외 없이 처리된다")
    void process_nullBalance_treatedAsZero() {
        Users user = user();
        Assets nullBalanceAsset = Assets.builder()
                .id(UUID.randomUUID()).institution("우리은행")
                .assetType(Assets.AccountType.CHECKING).balance(null)
                .bankType(Assets.BankType.WOORI).syncedAt(LocalDateTime.now()).build();
        given(assetRepository.findByUserIdAndDeletedAtIsNull(user.getId())).willReturn(List.of(nullBalanceAsset));

        AssetSnapshots result = processor.process(user);

        assertThat(result.getTotalAmount()).isZero();
        assertThat(result.getSavingsAmount()).isZero();
    }

    @Test
    @DisplayName("연동된 자산이 없는 유저는 모든 금액이 0인 스냅샷을 생성한다")
    void process_noAssets_zeroSnapshot() {
        Users user = user();
        given(assetRepository.findByUserIdAndDeletedAtIsNull(user.getId())).willReturn(List.of());

        AssetSnapshots result = processor.process(user);

        assertThat(result.getTotalAmount()).isZero();
        assertThat(result.getSavingsAmount()).isZero();
        assertThat(result.getInvestAmount()).isZero();
        assertThat(result.getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("같은 배치 실행(같은 processor 인스턴스) 내에서는 여러 유저를 처리해도 snapshotAt이 동일하다")
    void process_multipleUsers_shareSameSnapshotAt() {
        Users userA = user();
        Users userB = user();
        given(assetRepository.findByUserIdAndDeletedAtIsNull(userA.getId())).willReturn(List.of());
        given(assetRepository.findByUserIdAndDeletedAtIsNull(userB.getId())).willReturn(List.of());

        AssetSnapshots resultA = processor.process(userA);
        AssetSnapshots resultB = processor.process(userB);

        assertThat(resultA.getSnapshotAt()).isEqualTo(resultB.getSnapshotAt());
    }

    private Users user() {
        return Users.builder().id(UUID.randomUUID()).name("홍길동").build();
    }

    private Assets asset(Assets.AccountType type, Long balance) {
        return Assets.builder()
                .id(UUID.randomUUID())
                .institution("우리은행")
                .assetType(type)
                .balance(balance)
                .bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now())
                .build();
    }
}
