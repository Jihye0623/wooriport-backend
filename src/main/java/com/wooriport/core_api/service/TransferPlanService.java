package com.wooriport.core_api.service;

import static net.logstash.logback.argument.StructuredArguments.kv;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferPlanService {

    private final TransferPlanRepository transferPlanRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioFlowRepository portfolioFlowRepository;
    private final TransferExecutionRepository transferExecutionRepository;
    private final WebClient webClient;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

    // ──────────────────────────────────────
    // GET /transfer-plans
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public TransferPlanSummaryResponseDto getTransferPlans(UUID userId, int year, int month) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        List<TransferPlans> plans = transferPlanRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        // 이번달 실제 월급
        Long currentSalary = transactionRepository.findLatestSalaryTransaction(userId)
                .map(Transactions::getAmount).orElse(user.getSalary());

        // portfolios 기준값: assetId → Portfolios
        Map<UUID, Portfolios> portfolioByAssetId = portfolioRepository.findByUserId(userId).stream()
                .filter(p -> p.getAsset() != null)
                .collect(Collectors.toMap(p -> p.getAsset().getId(), Function.identity(), (a, b) -> a));

        // flow 기준값: gatheringAssetId → PortfolioFlows (amount + term 조회용)
        Map<UUID, PortfolioFlows> flowByAssetId = portfolioFlowRepository
                .findActiveByUserIdWithGatheringAsset(userId).stream()
                .collect(Collectors.toMap(f -> f.getGatheringAsset().getId(), f -> f, (a, b) -> a));
        Map<UUID, Long> flowBaselineByAssetId = flowByAssetId.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAmount()));

        // 플랜을 portfolio / flow 로 분류
        List<TransferPlanSummaryResponseDto.PortfolioPlanItem> portfolioItems = new ArrayList<>();
        List<TransferPlanSummaryResponseDto.FlowPlanItem> flowPlanItems = new ArrayList<>();

        for (TransferPlans plan : plans) {
            UUID assetId = plan.getAsset().getId();

            if (portfolioByAssetId.containsKey(assetId)) {
                Long baseline = portfolioByAssetId.get(assetId).getAssetAmount();
                portfolioItems.add(TransferPlanSummaryResponseDto.PortfolioPlanItem.builder()
                        .planId(plan.getId())
                        .assetId(assetId)
                        .institution(plan.getAsset().getInstitution())
                        .accountName(plan.getAsset().getAccountName())
                        .accountPurpose(plan.getAsset().getAccountPurpose())
                        .assetType(plan.getAssetType() != null ? plan.getAssetType().name() : null)
                        .plannedAmount(plan.getPlannedAmount())
                        .baselineAmount(baseline)
                        .diff(plan.getPlannedAmount() - baseline)
                        .isConfirmed(plan.getIsConfirmed())
                        .build());

            } else if (flowByAssetId.containsKey(assetId)) {
                PortfolioFlows flow = flowByAssetId.get(assetId);
                Long baseline = flow.getAmount();
                flowPlanItems.add(TransferPlanSummaryResponseDto.FlowPlanItem.builder()
                        .planId(plan.getId())
                        .assetId(assetId)
                        .institution(plan.getAsset().getInstitution())
                        .term(flow.getTerm())
                        .plannedAmount(plan.getPlannedAmount())
                        .baselineAmount(baseline)
                        .diff(plan.getPlannedAmount() - baseline)
                        .isConfirmed(plan.getIsConfirmed())
                        .build());
            }
        }

        // 생활비(출발 계좌) — 이체 대상이 아니라 우리은행에 '남기는 몫'이라 표시 전용으로 추가 (planId=null)
        // 단, 이번 달 플랜이 아직 없으면(첫 방문) 추가하지 않음 — 프론트의 빈 응답 → 자동생성 트리거를 막지 않기 위함
        UUID autoTransferAssetId = user.getAutoTransferToAssetId();
        if (!plans.isEmpty() && autoTransferAssetId != null) {
            Portfolios autoPortfolio = portfolioByAssetId.get(autoTransferAssetId);
            if (autoPortfolio != null) {
                Assets autoAsset = autoPortfolio.getAsset();
                long baseline = autoPortfolio.getAssetAmount();
                portfolioItems.add(0, TransferPlanSummaryResponseDto.PortfolioPlanItem.builder()
                        .planId(null)   // 실제 이체 계획이 아닌 표시 전용 (출발 계좌 자기이체 방지)
                        .assetId(autoTransferAssetId)
                        .institution(autoAsset.getInstitution())
                        .accountName(autoAsset.getAccountName())
                        .accountPurpose(autoAsset.getAccountPurpose())
                        .assetType(mapAccountTypeToCategory(autoAsset.getAssetType()).name())
                        .plannedAmount(baseline)
                        .baselineAmount(baseline)
                        .diff(0L)
                        .isConfirmed(false)
                        .build());
            }
        }

        long portfolioTotal    = portfolioItems.stream().mapToLong(TransferPlanSummaryResponseDto.PortfolioPlanItem::getPlannedAmount).sum();
        // 생활비(출발 계좌)도 표시·합계에 포함하므로 baseline 도 동일하게 전체 포함
        long portfolioBaseline = portfolioByAssetId.values().stream()
                .mapToLong(Portfolios::getAssetAmount).sum();
        long flowTotal         = flowPlanItems.stream().mapToLong(TransferPlanSummaryResponseDto.FlowPlanItem::getPlannedAmount).sum();

        long salary              = currentSalary != null ? currentSalary : 0L;
        long userSalary          = user.getSalary() != null ? user.getSalary() : 0L;
        long monthlyInvestAmount = user.getMonthlyInvestAmount() != null ? user.getMonthlyInvestAmount() : 0L;
        long remaining     = salary - portfolioTotal - flowTotal;
        long baseRemaining = userSalary - portfolioBaseline - monthlyInvestAmount;
        long remainingDiff = remaining - baseRemaining;

        // rebalanceComment 는 한 달치 플랜 중 한 행에만 저장되는데,
        // 조회 쿼리는 ORDER BY assetType 으로 정렬되어 저장 시의 get(0) 행과
        // 읽기 시의 get(0) 행이 달라질 수 있다. 정렬과 무관하게 값이 있는 행에서 가져온다.
        String rebalanceComment = plans.stream()
                .map(TransferPlans::getRebalanceComment)
                .filter(c -> c != null)
                .findFirst()
                .orElse(null);

        return TransferPlanSummaryResponseDto.builder()
                .currentSalary(currentSalary)
                .salaryDiff(currentSalary != null && user.getSalary() != null ? currentSalary - user.getSalary() : null)
                .portfolioTotal(portfolioTotal)
                .portfolioTotalDiff(portfolioTotal - portfolioBaseline)
                .portfolioItems(portfolioItems)
                .flowTotal(flowTotal)
                .flowTotalDiff(user.getMonthlyInvestAmount() != null ? flowTotal - monthlyInvestAmount : null)
                .flowItems(flowPlanItems)
                .remaining(remaining)
                .remainingDiff(remainingDiff)
                .rebalanceComment(rebalanceComment)
                .build();
    }

    // ──────────────────────────────────────
    // PATCH /transfer-plans?year=&month=
    // ──────────────────────────────────────
    @Transactional
    public void updateTransferPlans(UUID userId, int year, int month, List<TransferPlanUpdateRequestDto> requests) {
        List<TransferPlans> plans = transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month);

        Map<UUID, TransferPlans> planByAssetId = plans.stream()
                .collect(Collectors.toMap(p -> p.getAsset().getId(), Function.identity(), (a, b) -> a));

        for (TransferPlanUpdateRequestDto req : requests) {
            TransferPlans plan = planByAssetId.get(req.getAssetId());
            if (plan != null) {
                plan.updatePlannedAmount(req.getAmount());
            }
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

        // 2-1. 급여 감소 또는 +5만원 이상 증가 시 FastAPI 호출
        boolean isOutOfRange = user.getSalary() != null &&
                (monthlySalary < user.getSalary() || monthlySalary - user.getSalary() >= 50_000L);

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
        List<Portfolios> filteredPortfolios = portfolios.stream()
                .filter(p -> p.getAsset() != null)
                .filter(p -> !p.getAsset().getId().equals(user.getAutoTransferToAssetId()))
                .collect(Collectors.toList());

        List<TransferPlans> portfolioPlans = filteredPortfolios.stream()
                .map(p -> TransferPlans.builder()
                        .user(user)
                        .asset(p.getAsset())
                        .assetType(mapAccountTypeToCategory(p.getAsset().getAssetType()))
                        .plannedAmount(p.getAssetAmount())
                        .isConfirmed(false)
                        .year(year)
                        .month(month)
                        .build())
                .toList();
        plans.addAll(portfolioPlans);

        // 3-2. 활성 흐름 → gatheringAsset 기반 플랜
        List<PortfolioFlows> activeFlows = portfolioFlowRepository
                .findActiveByUserIdWithGatheringAsset(userId);

        List<TransferPlans> flowPlans = activeFlows.stream()
                .map(f -> TransferPlans.builder()
                        .user(user)
                        .asset(f.getGatheringAsset())
                        .assetType(mapAccountTypeToCategory(f.getGatheringAsset().getAssetType()))
                        .plannedAmount(f.getAmount())
                        .isConfirmed(false)
                        .year(year)
                        .month(month)
                        .build())
                .toList();
        plans.addAll(flowPlans);

        // 2-2. 급여 변동 시 FastAPI /salary로 금액 조정
        String rebalanceComment = null;
        if (isOutOfRange && !plans.isEmpty()) {
            Long salaryDiff = monthlySalary - user.getSalary();
            Map<String, Object> aiRes = callSalaryApi(userId, salaryDiff, filteredPortfolios, activeFlows);
            if (aiRes != null) {
                rebalanceComment = applyAiRebalancing(plans, aiRes);
                log.info("[generateFromSalary] AI 리밸런싱 적용 — salaryDiff: {}원", salaryDiff);
            }
        }

        if (rebalanceComment != null && !plans.isEmpty()) {
            plans.get(0).updateRebalanceComment(rebalanceComment);
        }

        transferPlanRepository.saveAll(plans);

        // 급여 변동 여부로 알림 문구 분기 (Case1: 변동없음 / Case2: 변동있음 — isOutOfRange 재사용)
        String salaryNotiContent = isOutOfRange
                ? "월급에 변동이 생겼어요! - 변동된 금액에 맞춰 PorTI 가이드를 다시 세워봐요!"
                : "급여가 들어왔어요 - PorTI의 월급 가이드를 확인하고 편하게 분배해봐요!";
        notificationService.saveAndSend(
                user.getId(),
                Notifications.NotificationType.SALARY_REBALANCING,
                "월급",
                salaryNotiContent);

        log.info("[TransferPlanService] 이체 계획 생성 완료 — userId: {}, 급여: {}원, portfolios: {}건, flowItems: {}건",
                userId, monthlySalary, portfolioPlans.size(), flowPlans.size());

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
    private Map<String, Object> callSalaryApi(UUID userId, Long salaryDiff,
                                               List<Portfolios> portfolios,
                                               List<PortfolioFlows> flows) {
        try {
            // 지난 1개월 카테고리별 지출 집계
            LocalDateTime from = LocalDateTime.now().minusMonths(1);
            LocalDateTime to   = LocalDateTime.now();
            List<Map<String, Object>> categoryExpense = transactionRepository
                    .findExpensesBetween(userId, from, to).stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getCategory() != null ? t.getCategory() : "기타",
                            Collectors.summingLong(t -> -t.getAmount())))
                    .entrySet().stream()
                    .map(e -> Map.<String, Object>of("name", e.getKey(), "expense", e.getValue()))
                    .collect(Collectors.toList());

            List<Map<String, Object>> portfolioList = portfolios.stream()
                    .map(p -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("asset_id",       p.getAsset().getId().toString());
                        m.put("account_purpose", p.getAsset().getAccountPurpose() != null
                                ? p.getAsset().getAccountPurpose() : "");
                        m.put("amount",         p.getAssetAmount());
                        return m;
                    })
                    .collect(Collectors.toList());

            List<Map<String, Object>> flowList = flows.stream()
                    .map(f -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("title", f.getTitle());
                        m.put("term", f.getTerm());
                        m.put("summary", f.getSummary());
                        m.put("asset_id", f.getGatheringAsset().getId().toString());
                        m.put("amount", f.getAmount());
                        return m;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> body = new HashMap<>();
            body.put("user_id", userId.toString());
            body.put("salary_diff", salaryDiff);
            body.put("category_expense", categoryExpense);
            body.put("portfolio_items", portfolioList);
            body.put("flow_items", flowList);

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
        Map<UUID, Long> aiAmounts = new HashMap<>();

        List<Map<String, Object>> portfolioItems = (List<Map<String, Object>>) aiRes.get("portfolio_items");
        if (portfolioItems != null) {
            portfolioItems.stream()
                    .filter(m -> m.get("asset_id") != null && m.get("amount") != null)
                    .forEach(m -> aiAmounts.put(
                            UUID.fromString((String) m.get("asset_id")),
                            ((Number) m.get("amount")).longValue()));
        }

        List<Map<String, Object>> flowItems = (List<Map<String, Object>>) aiRes.get("flow_items");
        if (flowItems != null) {
            flowItems.stream()
                    .filter(m -> m.get("asset_id") != null && m.get("amount") != null)
                    .forEach(m -> aiAmounts.put(
                            UUID.fromString((String) m.get("asset_id")),
                            ((Number) m.get("amount")).longValue()));
        }

        plans.forEach(p -> {
            Long aiAmount = aiAmounts.get(p.getAsset().getId());
            if (aiAmount != null) {
                p.updatePlannedAmount(aiAmount);
            }
        });

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
                log.warn("transfer_failed",
                        kv("event_type",  "transfer_failed"),
                        kv("amount",      plan.getPlannedAmount()),
                        kv("fail_reason", "잔액 부족"),
                        kv("plan_id",     plan.getId().toString()),
                        kv("user_id",     userId.toString()));

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

            log.info("transfer_executed",
                    kv("event_type", "transfer_executed"),
                    kv("amount",     plan.getPlannedAmount()),
                    kv("plan_id",    plan.getId().toString()),
                    kv("user_id",    userId.toString()));

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
        notificationService.saveAndSend(
                user.getId(),
                Notifications.NotificationType.SALARY_REBALANCING,
                "리밸런싱 완료",
                String.format("%d건 이체 완료, %d건 실패", successCount, failCount));

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
                        .assetType(p.getAssetType() != null ? p.getAssetType().name() : null)
                        .plannedAmount(p.getPlannedAmount())
                        .isConfirmed(p.getIsConfirmed())
                        .year(p.getYear())
                        .month(p.getMonth())
                        .build())
                .collect(Collectors.toList());

        // rebalanceComment 는 한 달치 플랜 중 한 행에만 저장되는데,
        // 조회 쿼리는 ORDER BY assetType 으로 정렬되어 저장 시의 get(0) 행과
        // 읽기 시의 get(0) 행이 달라질 수 있다. 정렬과 무관하게 값이 있는 행에서 가져온다.
        String rebalanceComment = plans.stream()
                .map(TransferPlans::getRebalanceComment)
                .filter(c -> c != null)
                .findFirst()
                .orElse(null);

        return TransferPlanListResponseDto.builder()
                .plans(items)
                .totalAmount(totalAmount)
                .rebalanceComment(rebalanceComment)
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
                        .assetType(p.getAssetType() != null ? p.getAssetType().name() : null)
                        .plannedAmount(p.getPlannedAmount())
                        .isConfirmed(p.getIsConfirmed())
                        .year(p.getYear())
                        .month(p.getMonth())
                        .build())
                .collect(Collectors.toList());

        // rebalanceComment 는 한 달치 플랜 중 한 행에만 저장되는데,
        // 조회 쿼리는 ORDER BY assetType 으로 정렬되어 저장 시의 get(0) 행과
        // 읽기 시의 get(0) 행이 달라질 수 있다. 정렬과 무관하게 값이 있는 행에서 가져온다.
        String rebalanceComment = plans.stream()
                .map(TransferPlans::getRebalanceComment)
                .filter(c -> c != null)
                .findFirst()
                .orElse(null);

        return TransferPlanListResponseDto.builder()
                .plans(items)
                .totalAmount(totalAmount)
                .salaryAmount(salaryAmount)
                .rebalanceComment(rebalanceComment)
                .build();
    }
}