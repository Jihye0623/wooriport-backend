package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transfer.TransferExecuteResultDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanListResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanSummaryResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanUpdateRequestDto;
import com.wooriport.core_api.base.exception.PortfolioNotSetException;
import com.wooriport.core_api.base.exception.SalaryNotFoundException;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.*;
import com.wooriport.core_api.domain.common.AssetCategory;
import com.wooriport.core_api.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferPlanServiceTest {

    @Mock TransferPlanRepository transferPlanRepository;
    @Mock AssetRepository assetRepository;
    @Mock UserRepository userRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock NotificationService notificationService;
    @Mock PortfolioRepository portfolioRepository;
    @Mock PortfolioFlowRepository portfolioFlowRepository;
    @Mock TransferExecutionRepository transferExecutionRepository;
    @Mock WebClient webClient;
    @InjectMocks TransferPlanService transferPlanService;

    private final UUID userId = UUID.randomUUID();
    private final int year = 2026;
    private final int month = 8;

    // ──────────────────────────────────────
    // getTransferPlans
    // ──────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 유저면 UserNotFoundException")
    void getTransferPlans_userNotFound_throws() {
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> transferPlanService.getTransferPlans(userId, year, month))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("portfolio 기준 플랜은 portfolioItems로 분류되고 diff = plannedAmount - baseline")
    void getTransferPlans_classifiesPortfolioPlan_andComputesDiff() {
        Users user = user(3_000_000L, null, 0L);
        Assets asset = asset(Assets.AccountType.CHECKING);
        Portfolios portfolio = Portfolios.builder().asset(asset).assetAmount(500_000L).build();
        TransferPlans plan = plan(asset, 600_000L, false, AssetCategory.CASH);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of(plan));
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.empty());
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of());

        TransferPlanSummaryResponseDto result = transferPlanService.getTransferPlans(userId, year, month);

        assertThat(result.getPortfolioItems()).hasSize(1);
        assertThat(result.getPortfolioItems().get(0).getDiff()).isEqualTo(100_000L);
        assertThat(result.getFlowItems()).isEmpty();
        assertThat(result.getCurrentSalary()).isEqualTo(3_000_000L); // 급여 트랜잭션 없어 user.salary로 폴백
    }

    @Test
    @DisplayName("flow 기준 플랜은 flowItems로 분류되고 diff = plannedAmount - flow.amount")
    void getTransferPlans_classifiesFlowPlan_andComputesDiff() {
        Users user = user(3_000_000L, null, 0L);
        Assets gatheringAsset = asset(Assets.AccountType.SAVINGS);
        PortfolioFlows flow = PortfolioFlows.builder()
                .title("흐름 A").term("단").amount(200_000L).gatheringAsset(gatheringAsset).build();
        TransferPlans plan = plan(gatheringAsset, 250_000L, false, AssetCategory.FIXED);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of(plan));
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.empty());
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of());
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of(flow));

        TransferPlanSummaryResponseDto result = transferPlanService.getTransferPlans(userId, year, month);

        assertThat(result.getFlowItems()).hasSize(1);
        assertThat(result.getFlowItems().get(0).getDiff()).isEqualTo(50_000L);
        assertThat(result.getFlowItems().get(0).getTerm()).isEqualTo("단");
        assertThat(result.getPortfolioItems()).isEmpty();
    }

    @Test
    @DisplayName("이번 달 플랜이 아직 없으면 생활비(출발 계좌) 표시 항목을 추가하지 않는다")
    void getTransferPlans_noPlansYet_doesNotAddLivingCostItem() {
        UUID wooriAssetId = UUID.randomUUID();
        Users user = user(3_000_000L, wooriAssetId, 0L);
        Assets wooriAsset = asset(Assets.AccountType.CHECKING);
        Portfolios wooriPortfolio = Portfolios.builder().asset(wooriAsset).assetAmount(1_000_000L).build();
        ReflectionTestUtils.setField(wooriAsset, "id", wooriAssetId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of());
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.empty());
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of(wooriPortfolio));
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of());

        TransferPlanSummaryResponseDto result = transferPlanService.getTransferPlans(userId, year, month);

        assertThat(result.getPortfolioItems()).isEmpty();
    }

    @Test
    @DisplayName("플랜이 있고 자동이체 출발 계좌가 설정되어 있으면 생활비 항목이 맨 앞에 diff=0으로 추가된다")
    void getTransferPlans_withPlansAndAutoTransferAsset_addsLivingCostItemFirst() {
        UUID wooriAssetId = UUID.randomUUID();
        Users user = user(3_000_000L, wooriAssetId, 0L);

        Assets wooriAsset = asset(Assets.AccountType.CHECKING);
        ReflectionTestUtils.setField(wooriAsset, "id", wooriAssetId);
        Portfolios wooriPortfolio = Portfolios.builder().asset(wooriAsset).assetAmount(1_000_000L).build();

        Assets otherAsset = asset(Assets.AccountType.SAVINGS);
        Portfolios otherPortfolio = Portfolios.builder().asset(otherAsset).assetAmount(300_000L).build();
        TransferPlans otherPlan = plan(otherAsset, 300_000L, false, AssetCategory.FIXED);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of(otherPlan));
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.empty());
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of(wooriPortfolio, otherPortfolio));
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of());

        TransferPlanSummaryResponseDto result = transferPlanService.getTransferPlans(userId, year, month);

        assertThat(result.getPortfolioItems()).hasSize(2);
        TransferPlanSummaryResponseDto.PortfolioPlanItem livingCost = result.getPortfolioItems().get(0);
        assertThat(livingCost.getPlanId()).isNull();
        assertThat(livingCost.getDiff()).isZero();
        assertThat(livingCost.getIsConfirmed()).isFalse();
        assertThat(livingCost.getPlannedAmount()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("자동이체 출발 계좌가 설정되지 않았으면 플랜이 있어도 생활비 항목을 추가하지 않는다")
    void getTransferPlans_noAutoTransferAsset_doesNotAddLivingCostItem() {
        Users user = user(3_000_000L, null, 0L);
        Assets asset = asset(Assets.AccountType.CHECKING);
        Portfolios portfolio = Portfolios.builder().asset(asset).assetAmount(500_000L).build();
        TransferPlans plan = plan(asset, 500_000L, false, AssetCategory.CASH);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of(plan));
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.empty());
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of());

        TransferPlanSummaryResponseDto result = transferPlanService.getTransferPlans(userId, year, month);

        assertThat(result.getPortfolioItems()).hasSize(1); // 실제 플랜 1건만, 생활비 항목 없음
    }

    @Test
    @DisplayName("최근 급여 트랜잭션이 있으면 그 금액이 currentSalary, salaryDiff = currentSalary - user.salary")
    void getTransferPlans_currentSalary_fromLatestSalaryTransaction() {
        Users user = user(3_000_000L, null, 0L);
        Transactions salaryTx = Transactions.builder().amount(3_200_000L).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of());
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.of(salaryTx));
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of());
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of());

        TransferPlanSummaryResponseDto result = transferPlanService.getTransferPlans(userId, year, month);

        assertThat(result.getCurrentSalary()).isEqualTo(3_200_000L);
        assertThat(result.getSalaryDiff()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("remaining = salary - portfolioTotal - flowTotal, remainingDiff = remaining - baseRemaining")
    void getTransferPlans_computesRemainingAndDiff() {
        Users user = user(3_000_000L, null, 500_000L); // monthlyInvestAmount=50만
        Assets asset = asset(Assets.AccountType.CHECKING);
        Portfolios portfolio = Portfolios.builder().asset(asset).assetAmount(1_000_000L).build();
        TransferPlans plan = plan(asset, 1_200_000L, false, AssetCategory.CASH);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of(plan));
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.empty());
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of());

        TransferPlanSummaryResponseDto result = transferPlanService.getTransferPlans(userId, year, month);

        // salary=3,000,000, portfolioTotal=1,200,000, flowTotal=0 → remaining = 1,800,000
        assertThat(result.getRemaining()).isEqualTo(1_800_000L);
        // baseRemaining = userSalary(3,000,000) - portfolioBaseline(1,000,000) - monthlyInvestAmount(500,000) = 1,500,000
        // remainingDiff = 1,800,000 - 1,500,000 = 300,000
        assertThat(result.getRemainingDiff()).isEqualTo(300_000L);
    }

    // ──────────────────────────────────────
    // updateTransferPlans
    // ──────────────────────────────────────

    @Test
    @DisplayName("요청에 매칭되는 assetId 플랜만 금액을 갱신하고, 갱신 시 확인 상태는 초기화된다")
    void updateTransferPlans_updatesOnlyMatchingAssetId_andResetsConfirmedFlag() {
        Assets asset = asset(Assets.AccountType.CHECKING);
        UUID assetId = UUID.randomUUID();
        ReflectionTestUtils.setField(asset, "id", assetId);
        TransferPlans plan = plan(asset, 100_000L, true, AssetCategory.CASH);

        TransferPlanUpdateRequestDto matching = updateRequest(assetId, 150_000L);
        TransferPlanUpdateRequestDto unmatched = updateRequest(UUID.randomUUID(), 999_000L);

        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of(plan));

        transferPlanService.updateTransferPlans(userId, year, month, List.of(matching, unmatched));

        assertThat(plan.getPlannedAmount()).isEqualTo(150_000L);
        assertThat(plan.getIsConfirmed()).isFalse(); // 금액 변경 시 재확인 필요
    }

    // ──────────────────────────────────────
    // generateFromSalary
    // ──────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 유저면 UserNotFoundException")
    void generateFromSalary_userNotFound_throws() {
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> transferPlanService.generateFromSalary(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("급여 트랜잭션이 없으면 SalaryNotFoundException")
    void generateFromSalary_noSalaryTransaction_throws() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user(3_000_000L, null, 0L)));
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> transferPlanService.generateFromSalary(userId))
                .isInstanceOf(SalaryNotFoundException.class);
    }

    @Test
    @DisplayName("포트폴리오가 설정되지 않았으면 PortfolioNotSetException")
    void generateFromSalary_noPortfolio_throws() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user(3_000_000L, null, 0L)));
        given(transactionRepository.findLatestSalaryTransaction(userId))
                .willReturn(Optional.of(Transactions.builder().amount(3_000_000L).build()));
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of());

        assertThatThrownBy(() -> transferPlanService.generateFromSalary(userId))
                .isInstanceOf(PortfolioNotSetException.class);
    }

    @Test
    @DisplayName("급여 변동이 범위 이내면 포트폴리오/흐름 기준 플랜만 생성하고 자동이체 출발 계좌는 제외한다")
    void generateFromSalary_withinRange_generatesPlansWithoutAiCall() {
        UUID wooriAssetId = UUID.randomUUID();
        Users user = user(3_000_000L, wooriAssetId, 0L);

        Assets wooriAsset = asset(Assets.AccountType.CHECKING);
        ReflectionTestUtils.setField(wooriAsset, "id", wooriAssetId);
        Portfolios wooriPortfolio = Portfolios.builder().asset(wooriAsset).assetAmount(999_999L).build();

        Assets otherAsset = asset(Assets.AccountType.SAVINGS);
        Portfolios otherPortfolio = Portfolios.builder().asset(otherAsset).assetAmount(500_000L).build();

        Assets flowAsset = asset(Assets.AccountType.DEPOSIT);
        PortfolioFlows flow = PortfolioFlows.builder()
                .title("흐름 A").term("단").amount(200_000L).gatheringAsset(flowAsset).build();

        // 급여 3,010,000 vs 기존 3,000,000 → +10,000, 범위(5만원) 이내 → isOutOfRange=false
        Transactions salaryTx = Transactions.builder().amount(3_010_000L).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.of(salaryTx));
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of(wooriPortfolio, otherPortfolio));
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of(flow));

        TransferPlanListResponseDto result = transferPlanService.generateFromSalary(userId);

        assertThat(result.getPlans()).hasSize(2); // wooriAsset 자기 자신은 제외
        assertThat(result.getTotalAmount()).isEqualTo(700_000L); // 500,000 + 200,000
        assertThat(result.getSalaryAmount()).isEqualTo(3_010_000L);
        assertThat(result.getRebalanceComment()).isNull();

        verify(transferPlanRepository).deleteByUserIdAndYearAndMonthAndIsConfirmedFalse(eq(userId), anyInt(), anyInt());
        verify(notificationService).saveAndSend(
                eq(userId), eq(Notifications.NotificationType.SALARY_REBALANCING),
                eq("월급"), eq("급여가 들어왔어요 - PorTI의 월급 가이드를 확인하고 편하게 분배해봐요!"));
    }

    @Test
    @DisplayName("급여가 크게 변동했지만 AI 서버 호출이 실패하면 포트폴리오 기준 금액을 그대로 유지한다")
    void generateFromSalary_outOfRangeButAiCallFails_fallsBackToPortfolioAmounts() {
        Users user = user(3_000_000L, null, 0L);

        Assets asset = asset(Assets.AccountType.SAVINGS);
        Portfolios portfolio = Portfolios.builder().asset(asset).assetAmount(500_000L).build();

        // 급여 3,000,000 → 3,060,000 (+60,000, 5만원 이상 증가) → isOutOfRange=true
        Transactions salaryTx = Transactions.builder().amount(3_060_000L).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transactionRepository.findLatestSalaryTransaction(userId)).willReturn(Optional.of(salaryTx));
        given(portfolioRepository.findByUserId(userId)).willReturn(List.of(portfolio));
        given(portfolioFlowRepository.findActiveByUserIdWithGatheringAsset(userId)).willReturn(List.of());
        given(transactionRepository.findExpensesBetween(any(), any(), any())).willReturn(List.of());
        // webClient는 unstubbed mock → post() 호출 시 null 반환 → 이어지는 체이닝에서 예외 발생 →
        // callSalaryApi의 catch(Exception)가 잡아서 null 반환 (AI 리밸런싱 미적용, 3번 플랜 그대로 유지)

        TransferPlanListResponseDto result = transferPlanService.generateFromSalary(userId);

        assertThat(result.getPlans()).hasSize(1);
        assertThat(result.getPlans().get(0).getPlannedAmount()).isEqualTo(500_000L); // 포트폴리오 기준 금액 유지
        assertThat(result.getRebalanceComment()).isNull();

        verify(notificationService).saveAndSend(
                eq(userId), eq(Notifications.NotificationType.SALARY_REBALANCING),
                eq("월급"), eq("월급에 변동이 생겼어요! - 변동된 금액에 맞춰 PorTI 가이드를 다시 세워봐요!"));
    }

    // ──────────────────────────────────────
    // confirmAndExecute
    // ──────────────────────────────────────

    @Test
    @DisplayName("이번 달 이체 계획이 없으면 IllegalStateException")
    void confirmAndExecute_noPlans_throws() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user(3_000_000L, UUID.randomUUID(), 0L)));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of());

        assertThatThrownBy(() -> transferPlanService.confirmAndExecute(userId, year, month))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("실행할 이체 계획이 없습니다");
    }

    @Test
    @DisplayName("자동이체 출발 계좌가 설정되지 않았으면 IllegalStateException")
    void confirmAndExecute_noAutoTransferAsset_throws() {
        Users user = user(3_000_000L, null, 0L);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month))
                .willReturn(List.of(plan(asset(Assets.AccountType.CHECKING), 100_000L, false, AssetCategory.CASH)));

        assertThatThrownBy(() -> transferPlanService.confirmAndExecute(userId, year, month))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자동이체 출발 계좌가 설정되지 않았습니다");
    }

    @Test
    @DisplayName("잔액이 충분하면 이체를 실행해 출발/목적 계좌 잔액이 대칭으로 변경되고 플랜이 확정된다")
    void confirmAndExecute_sufficientBalance_executesAndConfirms() {
        UUID wooriAssetId = UUID.randomUUID();
        Users user = user(3_000_000L, wooriAssetId, 0L);

        Assets wooriAsset = asset(Assets.AccountType.CHECKING);
        wooriAsset.updateBalance(1_000_000L);
        ReflectionTestUtils.setField(wooriAsset, "id", wooriAssetId);

        Assets targetAsset = asset(Assets.AccountType.SAVINGS);
        targetAsset.updateBalance(0L);

        TransferPlans plan = plan(targetAsset, 300_000L, false, AssetCategory.CASH);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of(plan));
        given(assetRepository.findById(wooriAssetId)).willReturn(Optional.of(wooriAsset));

        TransferExecuteResultDto result = transferPlanService.confirmAndExecute(userId, year, month);

        assertThat(wooriAsset.getBalance()).isEqualTo(700_000L);
        assertThat(targetAsset.getBalance()).isEqualTo(300_000L);
        assertThat(plan.getIsConfirmed()).isTrue();
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isZero();

        ArgumentCaptor<List<TransferExecutions>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferExecutionRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo(TransferExecutions.ExecutionStatus.COMPLETED);
    }

    @Test
    @DisplayName("잔액이 부족하면 해당 플랜은 실패 처리되고 잔액/확정 상태는 변경되지 않는다")
    void confirmAndExecute_insufficientBalance_marksFailedWithoutMutating() {
        UUID wooriAssetId = UUID.randomUUID();
        Users user = user(3_000_000L, wooriAssetId, 0L);

        Assets wooriAsset = asset(Assets.AccountType.CHECKING);
        wooriAsset.updateBalance(100_000L);
        ReflectionTestUtils.setField(wooriAsset, "id", wooriAssetId);

        Assets targetAsset = asset(Assets.AccountType.SAVINGS);
        targetAsset.updateBalance(0L);

        TransferPlans plan = plan(targetAsset, 300_000L, false, AssetCategory.CASH);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month)).willReturn(List.of(plan));
        given(assetRepository.findById(wooriAssetId)).willReturn(Optional.of(wooriAsset));

        TransferExecuteResultDto result = transferPlanService.confirmAndExecute(userId, year, month);

        assertThat(wooriAsset.getBalance()).isEqualTo(100_000L); // 변경 없음
        assertThat(targetAsset.getBalance()).isZero();
        assertThat(plan.getIsConfirmed()).isFalse();
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailCount()).isEqualTo(1);

        ArgumentCaptor<List<TransferExecutions>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferExecutionRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo(TransferExecutions.ExecutionStatus.FAILED);
    }

    @Test
    @DisplayName("여러 플랜을 순차 처리하며 앞선 이체로 줄어든 잔액을 다음 플랜의 잔액 체크에 반영한다")
    void confirmAndExecute_sequentialPlans_useUpdatedBalanceForEachCheck() {
        UUID wooriAssetId = UUID.randomUUID();
        Users user = user(3_000_000L, wooriAssetId, 0L);

        Assets wooriAsset = asset(Assets.AccountType.CHECKING);
        wooriAsset.updateBalance(500_000L);
        ReflectionTestUtils.setField(wooriAsset, "id", wooriAssetId);

        Assets targetAsset1 = asset(Assets.AccountType.SAVINGS);
        targetAsset1.updateBalance(0L);
        Assets targetAsset2 = asset(Assets.AccountType.DEPOSIT);
        targetAsset2.updateBalance(0L);

        TransferPlans plan1 = plan(targetAsset1, 400_000L, false, AssetCategory.CASH); // 성공: 500,000 → 100,000
        TransferPlans plan2 = plan(targetAsset2, 200_000L, false, AssetCategory.CASH); // 실패: 잔액 100,000 < 200,000

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month))
                .willReturn(List.of(plan1, plan2));
        given(assetRepository.findById(wooriAssetId)).willReturn(Optional.of(wooriAsset));

        TransferExecuteResultDto result = transferPlanService.confirmAndExecute(userId, year, month);

        assertThat(wooriAsset.getBalance()).isEqualTo(100_000L);
        assertThat(targetAsset1.getBalance()).isEqualTo(400_000L);
        assertThat(targetAsset2.getBalance()).isZero();
        assertThat(plan1.getIsConfirmed()).isTrue();
        assertThat(plan2.getIsConfirmed()).isFalse();
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getTotalCount()).isEqualTo(2);
    }

    // ──────────────────────────────────────
    // helpers
    // ──────────────────────────────────────

    private Users user(Long salary, UUID autoTransferToAssetId, Long monthlyInvestAmount) {
        return Users.builder()
                .id(userId)
                .name("홍길동")
                .salary(salary)
                .autoTransferToAssetId(autoTransferToAssetId)
                .monthlyInvestAmount(monthlyInvestAmount)
                .build();
    }

    private Assets asset(Assets.AccountType type) {
        return Assets.builder()
                .id(UUID.randomUUID())
                .institution("우리은행")
                .assetType(type)
                .balance(0L)
                .bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now())
                .build();
    }

    private TransferPlans plan(Assets asset, Long plannedAmount, boolean confirmed, AssetCategory type) {
        return TransferPlans.builder()
                .id(UUID.randomUUID())
                .asset(asset)
                .assetType(type)
                .plannedAmount(plannedAmount)
                .isConfirmed(confirmed)
                .year(year)
                .month(month)
                .build();
    }

    private TransferPlanUpdateRequestDto updateRequest(UUID assetId, Long amount) {
        TransferPlanUpdateRequestDto dto = new TransferPlanUpdateRequestDto();
        ReflectionTestUtils.setField(dto, "assetId", assetId);
        ReflectionTestUtils.setField(dto, "amount", amount);
        return dto;
    }
}
