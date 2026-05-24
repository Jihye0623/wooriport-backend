package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transfer.TransferExecuteResultDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanListResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanUpdateRequestDto;
import com.wooriport.core_api.domain.*;
import com.wooriport.core_api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferPlanService {

    private final TransferPlanRepository transferPlanRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransferExecutionRepository transferExecutionRepository;

    // ──────────────────────────────────────
    // GET /transfer-plans
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public TransferPlanListResponseDto getTransferPlans(UUID userId, int year, int month) {
        List<TransferPlans> plans = transferPlanRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        return toListResponse(plans);
    }

    // ──────────────────────────────────────
    // PATCH /transfer-plans/{id}
    // ──────────────────────────────────────
    @Transactional
    public void updateTransferPlan(UUID userId, UUID planId, TransferPlanUpdateRequestDto request) {
        TransferPlans plan = transferPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException("이체 계획을 찾을 수 없습니다."));

        if (request.getPlannedAmount() != null) {
            plan.updatePlannedAmount(request.getPlannedAmount()); // 내부에서 isConfirmed = false
        }
    }

    // 1. 급여 감지 → 이체 계획 자동 생성 + 알림
    @Transactional
    public TransferPlanListResponseDto generateFromSalary(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 최근 급여 트랜잭션 조회
        Transactions salaryTx = transactionRepository
                .findLatestSalaryTransaction(userId)
                .orElseThrow(() -> new IllegalStateException("급여 트랜잭션이 없습니다."));

        Long monthlySalary = salaryTx.getAmount();

        // 2. portfolios 조회 → 비율대로 계획 생성
        List<Portfolios> portfolios = portfolioRepository.findByUserId(userId);

        if (portfolios.isEmpty()) {
            throw new IllegalStateException("포트폴리오가 설정되지 않았습니다.");
        }

        int year  = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        // 3. 기존 이번 달 미확인 계획 삭제 후 재생성
        transferPlanRepository.deleteByUserIdAndYearAndMonthAndIsConfirmedFalse(
                userId, year, month);

        List<TransferPlans> plans = portfolios.stream()
                .filter(p -> p.getAsset() != null)  // 계좌 연동된 것만
                .filter(p -> !p.getAsset().getId().equals(user.getAutoTransferToAssetId())) // 우리은행 자기 자신 제외
                .map(p -> {
                    Long amount = monthlySalary * p.getAssetAmount() / 100;
                    return TransferPlans.builder()
                            .user(user)
                            .asset(p.getAsset())
                            .assetType(p.getAssetType())
                            .plannedAmount(amount)
                            .isConfirmed(false)       // 미확인 상태
                            .scheduledDate(user.getSalaryDate())
                            .year(year)
                            .month(month)
                            .transferScope(TransferPlans.TransferScope.PARTITION)
                            .build();
                })
                .toList();

        transferPlanRepository.saveAll(plans);

        // 4. 알림 발송 (사용자에게 확인 요청)
        Long totalAmount = plans.stream()
                .mapToLong(TransferPlans::getPlannedAmount)
                .sum();

        notificationRepository.save(Notifications.builder()
                .user(user)
                .type(Notifications.NotificationType.SALARY_REBALANCING)
                .title("월급 리밸런싱 계획이 준비됐어요")
                .content(String.format(
                        "이번 달 급여 %,d원 기준으로 %d개 계좌에 총 %,d원 이체 계획이 생성됐어요. 확인 후 실행해주세요.",
                        monthlySalary, plans.size(), totalAmount))
                .isRead(false)
                .sentAt(LocalDateTime.now())
                .build());

        log.info("[TransferPlanService] 이체 계획 생성 완료 — userId: {}, 급여: {}원, {}건",
                userId, monthlySalary, plans.size());

        return toListResponse(plans, monthlySalary);
    }


    // 2. 확인 → 즉시 실행 (기존 confirm-all 대체)
    @Transactional
    public TransferExecuteResultDto confirmAndExecute(UUID userId, int year, int month) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 이번 달 이체 계획 조회
        List<TransferPlans> plans = transferPlanRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        if (plans.isEmpty()) {
            throw new IllegalStateException("실행할 이체 계획이 없습니다.");
        }

        // 2. 우리은행 계좌 (출발 계좌)
        Assets wooriAsset = assetRepository
                .findById(user.getAutoTransferToAssetId())
                .orElseThrow(() -> new IllegalStateException("우리은행 계좌가 없습니다."));

        int successCount = 0;
        int failCount = 0;
        List<TransferExecutions> executions = new ArrayList<>();

        for (TransferPlans plan : plans) {

            // 3. 잔액 체크
            if (wooriAsset.getBalance() < plan.getPlannedAmount()) {
                log.warn("[confirmAndExecute] 잔액 부족 — planId: {}", plan.getId());

                executions.add(TransferExecutions.builder()
                        .plan(plan)
                        .user(user)
                        .fromAsset(wooriAsset)
                        .toAsset(plan.getAsset())
                        .amount(plan.getPlannedAmount())
                        .status(TransferExecutions.ExecutionStatus.FAILED)
                        .build());
                failCount++;
                continue;
            }

            // 4. 이체 실행
            wooriAsset.updateBalance(wooriAsset.getBalance() - plan.getPlannedAmount());
            plan.getAsset().updateBalance(plan.getAsset().getBalance() + plan.getPlannedAmount());
            plan.confirm();

            executions.add(TransferExecutions.builder()
                    .plan(plan)
                    .user(user)
                    .fromAsset(wooriAsset)
                    .toAsset(plan.getAsset())
                    .amount(plan.getPlannedAmount())
                    .status(TransferExecutions.ExecutionStatus.COMPLETED)
                    .executedAt(LocalDateTime.now())
                    .build());
            successCount++;
        }

        transferExecutionRepository.saveAll(executions);

        // 5. 완료 알림
        notificationRepository.save(Notifications.builder()
                .user(user)
                .type(Notifications.NotificationType.SALARY_REBALANCING)
                .title("리밸런싱 완료")
                .content(String.format("%d건 이체 완료, %d건 실패", successCount, failCount))
                .isRead(false)
                .sentAt(LocalDateTime.now())
                .build());

        log.info("[confirmAndExecute] 완료 — userId: {}, 성공: {}, 실패: {}",
                userId, successCount, failCount);

        return TransferExecuteResultDto.builder()
                .successCount(successCount)
                .failCount(failCount)
                .totalCount(plans.size())
                .build();
    }

    // ──────────────────────────────────────
    // 공통: Entity → Response 변환
    // ──────────────────────────────────────
    private TransferPlanListResponseDto toListResponse(List<TransferPlans> plans) {
        long totalAmount = plans.stream()
                .mapToLong(TransferPlans::getPlannedAmount)
                .sum();

        List<TransferPlanListResponseDto.PlanItem> items = plans.stream()
                .map(p -> TransferPlanListResponseDto.PlanItem.builder()
                        .id(p.getId())
                        .assetId(p.getAsset().getId())
                        .institution(p.getAsset().getInstitution())
                        .assetType(p.getAssetType().name())
                        .plannedAmount(p.getPlannedAmount())
                        .isConfirmed(p.getIsConfirmed())
                        .transferScope(p.getTransferScope().name())
                        .scheduledDate(p.getScheduledDate())
                        .year(p.getYear())
                        .month(p.getMonth())
                        .build())
                .collect(Collectors.toList());

        return TransferPlanListResponseDto.builder()
                .plans(items)
                .totalAmount(totalAmount)
                .build();
    }

    private TransferPlanListResponseDto toListResponse(List<TransferPlans> plans, Long salaryAmount) {
        long totalAmount = plans.stream()
                .mapToLong(TransferPlans::getPlannedAmount)
                .sum();

        List<TransferPlanListResponseDto.PlanItem> items = plans.stream()
                .map(p -> TransferPlanListResponseDto.PlanItem.builder()
                        .id(p.getId())
                        .assetId(p.getAsset().getId())
                        .institution(p.getAsset().getInstitution())
                        .assetType(p.getAssetType().name())
                        .plannedAmount(p.getPlannedAmount())
                        .isConfirmed(p.getIsConfirmed())
                        .transferScope(p.getTransferScope().name())
                        .scheduledDate(p.getScheduledDate())
                        .year(p.getYear())
                        .month(p.getMonth())
                        .build())
                .collect(Collectors.toList());

        return TransferPlanListResponseDto.builder()
                .plans(items)
                .totalAmount(totalAmount)
                .salaryAmount(salaryAmount)
                .build();
    }
}