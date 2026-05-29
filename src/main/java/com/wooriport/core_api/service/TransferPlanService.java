package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transfer.TransferExecuteResultDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanListResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanUpdateRequestDto;
import com.wooriport.core_api.base.exception.PortfolioNotSetException;
import com.wooriport.core_api.base.exception.SalaryNotFoundException;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.*;
import com.wooriport.core_api.domain.common.AssetCategory;
import com.wooriport.core_api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final PortfolioFlowItemRepository portfolioFlowItemRepository;
    private final WebClient webClient;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

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
                .orElseThrow(() -> new UserNotFoundException());

        // 1. 최근 급여 트랜잭션 조회
        Transactions salaryTx = transactionRepository
                .findLatestSalaryTransaction(userId)
                .orElseThrow(() -> new SalaryNotFoundException());

        Long monthlySalary = salaryTx.getAmount();

        // 2-1. 이전 급여 대비 ±5만원 초과 변동 여부 확인
        boolean isOutOfRange = user.getSalary() != null &&
                Math.abs(monthlySalary - user.getSalary()) >= 50_000L;

        // 3번: 포트폴리오 기반 플랜 생성 (항상)
        List<Portfolios> portfolios = portfolioRepository.findByUserId(userId);
        if (portfolios.isEmpty()) {
            throw new PortfolioNotSetException();
        }

        int year  = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        // 기존 미확인 계획 삭제
        transferPlanRepository.deleteByUserIdAndYearAndMonthAndIsConfirmedFalse(userId, year, month);

        List<TransferPlans> plans = new ArrayList<>();

        // 3-1. portfolios → asset_amount 기반 플랜
        List<TransferPlans> portfolioPlans = portfolios.stream()
                .filter(p -> p.getAsset() != null)
                .filter(p -> !p.getAsset().getId().equals(user.getAutoTransferToAssetId()))
                .map(p -> TransferPlans.builder()
                        .user(user)
                        .asset(p.getAsset())
                        .assetType(p.getAssetType())
                        .plannedAmount(p.getAssetAmount())
                        .isConfirmed(false)
                        .scheduledDate(user.getSalaryDate())
                        .year(year)
                        .month(month)
                        .build())
                .toList();
        plans.addAll(portfolioPlans);

        // 3-2. portfolio_flow_items (startedAt IS NOT NULL인 흐름) → amount 기반 플랜
        List<PortfolioFlowItems> flowItems = portfolioFlowItemRepository
                .findActiveFlowItemsWithAmountByUserId(userId);

        List<TransferPlans> flowItemPlans = flowItems.stream()
                .filter(pi -> !pi.getAsset().getId().equals(user.getAutoTransferToAssetId()))
                .map(pi -> TransferPlans.builder()
                        .user(user)
                        .asset(pi.getAsset())
                        .assetType(mapAccountTypeToCategory(pi.getAsset().getAssetType()))
                        .plannedAmount(pi.getFlow().getAmount() * pi.getProductRatio() / 100)
                        .isConfirmed(false)
                        .scheduledDate(user.getSalaryDate())
                        .year(year)
                        .month(month)
                        .build())
                .toList();
        plans.addAll(flowItemPlans);

        // 2-2. ±5만원 초과 변동 시 FastAPI /salary로 금액 조정
        String rebalanceComment = null;
        if (isOutOfRange && !plans.isEmpty()) {
            Long salaryDiff = monthlySalary - user.getSalary();
            Map<String, Object> aiRes = callSalaryApi(userId, salaryDiff, plans);
            if (aiRes != null) {
                rebalanceComment = applyAiRebalancing(plans, aiRes);
                log.info("[generateFromSalary] AI 리밸런싱 적용 — salaryDiff: {}원", salaryDiff);
            }
        }

        transferPlanRepository.saveAll(plans);

        // user.salary 갱신
        user.updateSalary(monthlySalary);

        // 알림 저장
        Long totalAmount = plans.stream().mapToLong(TransferPlans::getPlannedAmount).sum();
        String notifContent = rebalanceComment != null
                ? rebalanceComment
                : String.format("이번 달 급여 %,d원 기준으로 %d개 계좌에 총 %,d원 이체 계획이 생성됐어요. 확인 후 실행해주세요.",
                        monthlySalary, plans.size(), totalAmount);

        notificationRepository.save(Notifications.builder()
                .user(user)
                .type(Notifications.NotificationType.SALARY_REBALANCING)
                .title("월급 리밸런싱 계획이 준비됐어요")
                .content(notifContent)
                .isRead(false)
                .sentAt(LocalDateTime.now())
                .build());

        log.info("[TransferPlanService] 이체 계획 생성 완료 — userId: {}, 급여: {}원, portfolios: {}건, flowItems: {}건",
                userId, monthlySalary, portfolioPlans.size(), flowItemPlans.size());

        return toListResponse(plans, monthlySalary);
    }

    private AssetCategory mapAccountTypeToCategory(Assets.AccountType accountType) {
        if (accountType == null) return AssetCategory.CASH;
        return switch (accountType) {
            case SAVINGS -> AssetCategory.FIXED;
            case DEPOSIT -> AssetCategory.DEPOSIT;
            case STOCK   -> AssetCategory.STOCK;
            case IRP, ISA -> AssetCategory.IRP;
            default      -> AssetCategory.CASH;
        };
    }

    // FastAPI POST /salary 호출
    @SuppressWarnings("unchecked")
    private Map<String, Object> callSalaryApi(UUID userId, Long salaryDiff, List<TransferPlans> plans) {
        try {
            List<Map<String, Object>> rebalanceList = plans.stream()
                    .map(p -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("asset_id", p.getAsset().getId().toString());
                        String category = p.getAsset().getAccountPurpose() != null
                                ? p.getAsset().getAccountPurpose()
                                : p.getAssetType().name();
                        m.put("category", category);
                        m.put("amount", p.getPlannedAmount());
                        return m;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> body = new HashMap<>();
            body.put("user_id", userId.toString());
            body.put("salary_diff", salaryDiff);
            body.put("salary_rebalance", rebalanceList);

            return webClient.post()
                    .uri(flaskMlUrl + "/salary")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("[generateFromSalary] FastAPI /salary 호출 실패 — 3번 플랜 유지: {}", e.getMessage());
            return null;
        }
    }

    // AI 응답으로 플랜 금액 업데이트, rebalance_comment 반환
    @SuppressWarnings("unchecked")
    private String applyAiRebalancing(List<TransferPlans> plans, Map<String, Object> aiRes) {
        List<Map<String, Object>> aiRebalance = (List<Map<String, Object>>) aiRes.get("salary_rebalance");
        if (aiRebalance != null) {
            Map<UUID, Long> aiAmounts = aiRebalance.stream()
                    .filter(m -> m.get("asset_id") != null && m.get("amount") != null)
                    .collect(Collectors.toMap(
                            m -> UUID.fromString((String) m.get("asset_id")),
                            m -> ((Number) m.get("amount")).longValue(),
                            (a, b) -> a));

            plans.forEach(p -> {
                Long aiAmount = aiAmounts.get(p.getAsset().getId());
                if (aiAmount != null) {
                    p.updatePlannedAmount(aiAmount);
                }
            });
        }
        return (String) aiRes.get("rebalance_comment");
    }

    // 2. 확인 → 즉시 실행 (기존 confirm-all 대체)
    @Transactional
    public TransferExecuteResultDto confirmAndExecute(UUID userId, int year, int month) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        // 1. 이번 달 이체 계획 조회
        List<TransferPlans> plans = transferPlanRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        if (plans.isEmpty()) {
            throw new IllegalStateException("실행할 이체 계획이 없습니다.");
        }

        // 2. 우리은행 계좌 (출발 계좌)
        UUID autoTransferAssetId = user.getAutoTransferToAssetId();
        if (autoTransferAssetId == null) {
            throw new IllegalStateException("자동이체 출발 계좌가 설정되지 않았습니다.");
        }
        Assets wooriAsset = assetRepository
                .findById(autoTransferAssetId)
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