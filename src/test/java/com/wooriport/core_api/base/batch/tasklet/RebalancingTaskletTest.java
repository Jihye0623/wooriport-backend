package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
import com.wooriport.core_api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RebalancingTaskletTest {

    @Mock UserRepository userRepository;
    @Mock AssetRepository assetRepository;
    @Mock PortfolioRepository portfolioRepository;
    @InjectMocks RebalancingTasklet rebalancingTasklet;

    @Test
    @DisplayName("오늘 급여일 대상자가 없으면 FINISHED만 반환하고 아무 것도 하지 않는다")
    void noTargetsToday_returnsFinished_noOp() {
        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of());

        RepeatStatus result = rebalancingTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(assetRepository, never()).findById(any());
        verify(portfolioRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("자동이체 출발 계좌(우리은행)가 설정되지 않은 유저는 스킵한다")
    void userWithoutAutoTransferAsset_skipped() {
        Users user = user(null);
        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));

        RepeatStatus result = rebalancingTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(assetRepository, never()).findById(any());
    }

    @Test
    @DisplayName("우리은행 계좌 잔액이 0 이하면 스킵하고 포트폴리오를 조회하지 않는다")
    void wooriBalanceZeroOrLess_skipped() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets wooriAsset = asset(wooriId, Assets.AccountType.CHECKING, 0L);

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));

        rebalancingTasklet.execute(null, null);

        verify(portfolioRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("포트폴리오가 설정되지 않았으면 스킵하고 잔액은 변경되지 않는다")
    void noPortfolios_skipped_balanceUnchanged() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets wooriAsset = asset(wooriId, Assets.AccountType.CHECKING, 1_000_000L);

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));
        given(portfolioRepository.findByUserId(user.getId())).willReturn(List.of());

        rebalancingTasklet.execute(null, null);

        assertThat(wooriAsset.getBalance()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("정상 분배: 우리은행 계좌에서 차감한 만큼 각 목적 계좌로 정확히 입금된다")
    void distributesAcrossPortfolios_symmetrically() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets wooriAsset = asset(wooriId, Assets.AccountType.CHECKING, 1_000_000L);

        Assets assetA = asset(UUID.randomUUID(), Assets.AccountType.SAVINGS, 0L);
        Assets assetB = asset(UUID.randomUUID(), Assets.AccountType.DEPOSIT, 0L);
        Portfolios portfolioA = Portfolios.builder().asset(assetA).assetAmount(300_000L).build();
        Portfolios portfolioB = Portfolios.builder().asset(assetB).assetAmount(200_000L).build();

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));
        given(portfolioRepository.findByUserId(user.getId())).willReturn(List.of(portfolioA, portfolioB));

        rebalancingTasklet.execute(null, null);

        assertThat(wooriAsset.getBalance()).isEqualTo(500_000L); // 1,000,000 - 300,000 - 200,000
        assertThat(assetA.getBalance()).isEqualTo(300_000L);
        assertThat(assetB.getBalance()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("우리은행 계좌 자기 자신에 대한 포트폴리오 항목은 분배 대상에서 제외한다")
    void skipsSelfPortfolioItem() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets wooriAsset = asset(wooriId, Assets.AccountType.CHECKING, 1_000_000L);
        Portfolios selfPortfolio = Portfolios.builder().asset(wooriAsset).assetAmount(400_000L).build();

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));
        given(portfolioRepository.findByUserId(user.getId())).willReturn(List.of(selfPortfolio));

        rebalancingTasklet.execute(null, null);

        assertThat(wooriAsset.getBalance()).isEqualTo(1_000_000L); // 변동 없음
    }

    @Test
    @DisplayName("계좌 미연동 포트폴리오(asset=null)는 스킵하고 예외 없이 계속 처리한다")
    void skipsUnlinkedPortfolio_noException() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets wooriAsset = asset(wooriId, Assets.AccountType.CHECKING, 1_000_000L);
        Portfolios unlinked = Portfolios.builder().asset(null).assetAmount(300_000L).build();

        Assets assetA = asset(UUID.randomUUID(), Assets.AccountType.SAVINGS, 0L);
        Portfolios linked = Portfolios.builder().asset(assetA).assetAmount(100_000L).build();

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));
        given(portfolioRepository.findByUserId(user.getId())).willReturn(List.of(unlinked, linked));

        rebalancingTasklet.execute(null, null);

        assertThat(wooriAsset.getBalance()).isEqualTo(900_000L); // 미연동 항목은 무시, linked만 반영
        assertThat(assetA.getBalance()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("포트폴리오 금액이 0 이하면 해당 항목은 분배하지 않는다")
    void skipsNonPositiveAmount() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets wooriAsset = asset(wooriId, Assets.AccountType.CHECKING, 1_000_000L);
        Assets assetA = asset(UUID.randomUUID(), Assets.AccountType.SAVINGS, 0L);
        Portfolios zeroPortfolio = Portfolios.builder().asset(assetA).assetAmount(0L).build();

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));
        given(portfolioRepository.findByUserId(user.getId())).willReturn(List.of(zeroPortfolio));

        rebalancingTasklet.execute(null, null);

        assertThat(wooriAsset.getBalance()).isEqualTo(1_000_000L);
        assertThat(assetA.getBalance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("한 유저 처리 중 예외가 발생해도 나머지 유저는 계속 처리된다")
    void oneUserFails_othersStillProcessed() {
        UUID failingWooriId = UUID.randomUUID();
        Users failingUser = user(failingWooriId);

        UUID okWooriId = UUID.randomUUID();
        Users okUser = user(okWooriId);
        Assets okWooriAsset = asset(okWooriId, Assets.AccountType.CHECKING, 1_000_000L);
        Assets okTargetAsset = asset(UUID.randomUUID(), Assets.AccountType.SAVINGS, 0L);
        Portfolios okPortfolio = Portfolios.builder().asset(okTargetAsset).assetAmount(400_000L).build();

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(failingUser, okUser));
        given(assetRepository.findById(failingWooriId)).willReturn(Optional.empty()); // 우리은행 계좌 없음 -> 예외
        given(assetRepository.findById(okWooriId)).willReturn(Optional.of(okWooriAsset));
        given(portfolioRepository.findByUserId(okUser.getId())).willReturn(List.of(okPortfolio));

        RepeatStatus result = rebalancingTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        assertThat(okWooriAsset.getBalance()).isEqualTo(600_000L);
        assertThat(okTargetAsset.getBalance()).isEqualTo(400_000L);
    }

    private Users user(UUID autoTransferToAssetId) {
        return Users.builder()
                .id(UUID.randomUUID())
                .name("홍길동")
                .autoTransferToAssetId(autoTransferToAssetId)
                .build();
    }

    private Assets asset(UUID id, Assets.AccountType type, Long balance) {
        return Assets.builder()
                .id(id)
                .institution("우리은행")
                .assetType(type)
                .balance(balance)
                .bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now())
                .build();
    }
}
