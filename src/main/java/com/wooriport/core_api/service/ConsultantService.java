package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.consultant.*;
import com.wooriport.core_api.base.dto.dashboard.DashboardResponseDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowUpdateRequestDto;
import com.wooriport.core_api.domain.PortfolioFlowItems;
import com.wooriport.core_api.domain.PortfolioFlows;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.PortfolioFlowRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultantService {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioFlowRepository portfolioFlowRepository;
    private final PortfolioFlowService portfolioFlowService;
    private final WebClient webClient;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

    private static final List<String> INVEST_KEYWORDS =
            List.of("ETF", "주식", "투자", "채권", "적금", "예금", "저축", "현금성", "파킹", "CMA", "IRP", "연금", "비상금");

    // ──────────────────────────────────────
    // POST /consultant/analyze
    // ──────────────────────────────────────
    public ConsultantAnalyzeResponseDto analyze(UUID userId, ConsultantAnalyzeRequestDto request) {
        Map<String, Object> snapshot = buildSnapshot(userId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flows = (List<Map<String, Object>>) snapshot.getOrDefault("flows", List.of());
        boolean hasInvestmentFlows = !flows.isEmpty();

        Map<String, Object> body = new HashMap<>();
        body.put("user_goal", request.getUserGoal());
        body.put("dashboard_snapshot", snapshot);

        Map<String, Object> res = callFlask("/consultant/analyze", body);
        return ConsultantAnalyzeResponseDto.builder()
                .action((String) res.get("action"))
                .reasoning((String) res.get("reasoning"))
                .hasInvestmentFlows(hasInvestmentFlows)
                .build();
    }

    // ──────────────────────────────────────
    // POST /consultant/propose
    // ──────────────────────────────────────
    @Transactional
    public ConsultantProposeResponseDto propose(UUID userId, ConsultantProposeRequestDto request) {
        Map<String, Object> snapshot = buildSnapshot(userId);

        // 스냅샷에서 이미 조회된 데이터 재활용 — flowId→title, productId→name 맵 구성
        Map<String, String> productNameMap = new HashMap<>();
        Map<String, String> flowTitleMap = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshotFlows = (List<Map<String, Object>>) snapshot.getOrDefault("flows", List.of());
        for (Map<String, Object> sf : snapshotFlows) {
            Object fid = sf.get("flowId");
            Object ftitle = sf.get("title");
            if (fid instanceof String fs && ftitle instanceof String ft) flowTitleMap.put(fs, ft);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sfProds = (List<Map<String, Object>>) sf.getOrDefault("products", List.of());
            for (Map<String, Object> sp : sfProds) {
                Object pid = sp.get("productId");
                Object pname = sp.get("productName");
                if (pid instanceof String s && pname instanceof String n) productNameMap.put(s, n);
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_goal", request.getUserGoal());
        body.put("action", request.getAction());
        body.put("dashboard_snapshot", snapshot);

        Map<String, Object> res = callFlask("/consultant/propose", body);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawAllocations =
                (List<Map<String, Object>>) res.getOrDefault("salary_allocations", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawPortfolio =
                (List<Map<String, Object>>) res.getOrDefault("portfolio", List.of());

        List<ConsultantProposeResponseDto.SalaryAllocation> salaryAllocations = rawAllocations.stream()
                .map(a -> ConsultantProposeResponseDto.SalaryAllocation.builder()
                        .purpose((String) a.getOrDefault("purpose", "기타"))
                        .plannedAmount(toInt(a.get("plannedAmount")))
                        .ratio(toInt(a.get("ratio")))
                        .build())
                .collect(Collectors.toList());

        List<ConsultantProposeResponseDto.PortfolioItem> portfolio = rawPortfolio.stream()
                .map(p -> ConsultantProposeResponseDto.PortfolioItem.builder()
                        .assetType((String) p.getOrDefault("assetType", ""))
                        .ratio(toInt(p.get("ratio")))
                        .build())
                .collect(Collectors.toList());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawFlows =
                (List<Map<String, Object>>) res.get("flows");
        if (rawFlows == null) rawFlows = List.of();

        List<ConsultantProposeResponseDto.FlowUpdate> flows = rawFlows.stream()
                .map(f -> {
                    String fid = (String) f.get("flowId");
                    Object rawAmount = f.get("amount");
                    Long amount = rawAmount instanceof Number n ? n.longValue() : null;
                    return ConsultantProposeResponseDto.FlowUpdate.builder()
                            .flowId(fid)
                            .flowTitle(flowTitleMap.get(fid))
                            .amount(amount)
                            .products(((List<Map<String, Object>>) f.getOrDefault("products", List.of())).stream()
                                    .map(p -> {
                                        String pid = (String) p.get("productId");
                                        return ConsultantProposeResponseDto.FlowUpdate.ProductItem.builder()
                                                .productId(pid)
                                                .productName(pid != null ? productNameMap.get(pid) : null)
                                                .productRatio(toInt(p.get("productRatio")))
                                                .build();
                                    })
                                    .collect(Collectors.toList()))
                            .build();
                })
                .collect(Collectors.toList());

        return ConsultantProposeResponseDto.builder()
                .summary((String) res.getOrDefault("summary", ""))
                .explanation((String) res.getOrDefault("explanation", ""))
                .salaryAllocations(salaryAllocations)
                .portfolio(portfolio)
                .flows(flows)
                .build();
    }

    // ──────────────────────────────────────
    // POST /consultant/apply
    // ──────────────────────────────────────
    @Transactional
    public void apply(UUID userId, ConsultantApplyRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        List<Portfolios> currentPortfolios = portfolioRepository.findByUserId(userId);
        long currentInvestAmount = user.getMonthlyInvestAmount() == null ? 0L : user.getMonthlyInvestAmount();
        long monthlyIncome = user.getSalary() == null ? 0L : user.getSalary();

        long newInvestAmount;

        if ("portfolio".equals(request.getAction())) {

            newInvestAmount = currentInvestAmount > 0
                    ? currentInvestAmount
                    : Math.round(monthlyIncome * 0.2);

            if (request.getFlows() != null) {
                for (ConsultantApplyRequestDto.FlowUpdate flowUpdate : request.getFlows()) {
                    try {
                        UUID flowId = UUID.fromString(flowUpdate.getFlowId());
                        PortfolioFlowUpdateRequestDto flowRequest = new PortfolioFlowUpdateRequestDto();
                        List<PortfolioFlowUpdateRequestDto.ProductItem> products = flowUpdate.getProducts().stream()
                                .map(p -> {
                                    PortfolioFlowUpdateRequestDto.ProductItem pi = new PortfolioFlowUpdateRequestDto.ProductItem();
                                    pi.setProductId(UUID.fromString(p.getProductId()));
                                    pi.setProductRatio(p.getProductRatio());
                                    return pi;
                                })
                                .collect(Collectors.toList());
                        flowRequest.setProducts(products);
                        portfolioFlowService.updateFlow(userId, flowId, flowRequest);
                    } catch (Exception e) {
                        log.warn("[ConsultantService] 흐름 수정 실패 — flowId={}: {}", flowUpdate.getFlowId(), e.getMessage());
                    }
                }
            }

        } else {
            // salary action: AI 제안 배분 중 투자성 항목 합계 → 새 투자 금액
            newInvestAmount = request.getSalaryAllocations() == null ? 0L
                    : request.getSalaryAllocations().stream()
                            .filter(a -> a.getPurpose() != null &&
                                    INVEST_KEYWORDS.stream().anyMatch(kw -> a.getPurpose().contains(kw)))
                            .mapToLong(a -> (long) a.getPlannedAmount())
                            .sum();

            if (newInvestAmount <= 0) {
                newInvestAmount = currentInvestAmount > 0
                        ? currentInvestAmount
                        : Math.round(monthlyIncome * 0.2);
            }

            // AI 제안 salaryAllocations를 purpose 매칭으로 portfolios에 직접 반영
            // (투자성 항목 제외한 생활비·저축 등 변경분을 실제 DB에 저장)
            if (request.getSalaryAllocations() != null && !request.getSalaryAllocations().isEmpty()) {
                request.getSalaryAllocations().stream()
                        .filter(a -> a.getPurpose() != null &&
                                INVEST_KEYWORDS.stream().noneMatch(kw -> a.getPurpose().contains(kw)))
                        .forEach(proposed -> currentPortfolios.stream()
                                .filter(p -> p.getAsset() != null &&
                                        proposed.getPurpose().equals(p.getAsset().getAccountPurpose()))
                                .findFirst()
                                .ifPresent(p -> p.updateAmount((long) proposed.getPlannedAmount())));
            }
        }

        if (currentInvestAmount > 0 && newInvestAmount != currentInvestAmount) {
            final long finalNewInvestAmount = newInvestAmount;
            portfolioFlowRepository.findAllByUserIdWithItems(userId).stream()
                    .filter(flow -> flow.getAmount() != null)
                    .forEach(flow ->
                            flow.updateAmount(flow.getAmount() * finalNewInvestAmount / currentInvestAmount));
        }

        user.updateMonthlyInvestAmount(newInvestAmount);
        log.info("[ConsultantService] apply — userId: {}, action: {}, {}원 → {}원",
                userId, request.getAction(), currentInvestAmount, newInvestAmount);
    }

    // ──────────────────────────────────────
    // 대시보드 스냅샷 구성 (AI 서버 전달용)
    // ──────────────────────────────────────
    private Map<String, Object> buildSnapshot(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        DashboardResponseDto dashboard = dashboardService.getDashboard(userId);
        DashboardResponseDto.SalaryPlan sp = dashboard.getSalaryPlan();

        List<Map<String, Object>> allocations = sp.getAllocations() == null
                ? List.of()
                : sp.getAllocations().stream().map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("purpose", a.getPurpose() != null ? a.getPurpose() : "기타");
                    m.put("plannedAmount", a.getPlannedAmount() != null ? a.getPlannedAmount() : 0);
                    m.put("ratio", sp.getMonthlyIncome() != null && sp.getMonthlyIncome() > 0
                            ? (int) Math.round((a.getPlannedAmount() != null ? a.getPlannedAmount() : 0)
                                    * 100.0 / sp.getMonthlyIncome())
                            : 0);
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> salaryPlan = new HashMap<>();
        salaryPlan.put("monthlyIncome", sp.getMonthlyIncome());
        salaryPlan.put("investmentAmount", sp.getInvestmentAmount());
        salaryPlan.put("allocations", allocations);

        long totalExpense = dashboard.getConsumption() != null
                && dashboard.getConsumption().getTotalExpense() != null
                ? dashboard.getConsumption().getTotalExpense() : 0L;

        List<Map<String, Object>> flowItems = portfolioFlowRepository.findAllByUserIdWithDetails(userId).stream()
                .map(flow -> {
                    List<Map<String, Object>> products = flow.getItems().stream()
                            .filter(PortfolioFlowItems::isPut)
                            .filter(i -> i.getProduct() != null)
                            .map(i -> {
                                Map<String, Object> pm = new HashMap<>();
                                pm.put("productId", i.getProduct().getId().toString());
                                pm.put("productName", i.getProduct().getName());
                                pm.put("productType", i.getProduct().getProductType() != null
                                        ? i.getProduct().getProductType().name() : null);
                                pm.put("productRatio", i.getProductRatio() != null ? i.getProductRatio() : 0);
                                return pm;
                            })
                            .collect(Collectors.toList());
                    Map<String, Object> fm = new HashMap<>();
                    fm.put("flowId", flow.getId().toString());
                    fm.put("title", flow.getTitle());
                    fm.put("products", products);
                    return fm;
                })
                .filter(fm -> !((List<?>) fm.get("products")).isEmpty())
                .collect(Collectors.toList());

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("salaryPlan", salaryPlan);
        snapshot.put("flows", flowItems);
        snapshot.put("totalExpense", totalExpense);
        snapshot.put("portiType", user.getPortiType() != null ? user.getPortiType().name() : null);
        snapshot.put("portiComment", user.getPortiComment());
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callFlask(String path, Map<String, Object> body) {
        try {
            Map<String, Object> response = webClient.post()
                    .uri(flaskMlUrl + path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) throw new IllegalStateException("AI 서버 응답이 없습니다.");
            return response;
        } catch (Exception e) {
            log.error("AI 서버 호출 실패 [{}]: {}", path, e.getMessage());
            throw new IllegalStateException("AI 서버 호출 실패: " + e.getMessage(), e);
        }
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}
