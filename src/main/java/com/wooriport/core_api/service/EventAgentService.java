package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.event.*;
import com.wooriport.core_api.domain.*;
import com.wooriport.core_api.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventAgentService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final EventRepository eventRepository;
    private final EventPortfoliosRepository eventPortfoliosRepository;
    private final SpendingBudgetRepository spendingBudgetRepository;

    private final WebClient webClient;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

    private static final String GOAL_BASE = "/agent/goal";

    // ──────────────────────────────────────
    // [STEP 2] 결혼 예산 산정
    // ──────────────────────────────────────
    public WeddingBudgetResponseDto estimateWedding(UUID userId, WeddingRequestDto req) {
        Map<String, Object> body = Map.of(
                "user_id",         userId.toString(),
                "deadline",        req.getDeadline().toString(),
                "wedding_region",  req.getWeddingRegion(),
                "wedding_month",   req.getWeddingMonth(),
                "honeymoon_scale", req.getHoneymoonScale(),
                "sdrme_scale",     req.getSdrmeScale()
        );

        Map<String, Object> res = callFlask(GOAL_BASE + "/wedding", body);
        Map<String, Object> budget = cast(res.get("budget"));

        return WeddingBudgetResponseDto.builder()
                .budget(WeddingBudgetResponseDto.WeddingBudget.builder()
                        .venue(toLong(budget.get("venue")))
                        .honeymoon(toLong(budget.get("honeymoon")))
                        .sdrme(toLong(budget.get("sdrme")))
                        .total(toLong(budget.get("total")))
                        .build())
                .build();
    }

    // ──────────────────────────────────────
    // [STEP 2] 여행 예산 산정
    // ──────────────────────────────────────
    public TravelBudgetResponseDto estimateTravel(UUID userId, TravelRequestDto req) {
        Map<String, Object> body = Map.of(
                "user_id",        userId.toString(),
                "deadline",       req.getDeadline().toString(),
                "maximum_budget", req.getMaximumBudget(),
                "destination",    req.getDestination(),
                "travel_style",   req.getTravelStyle(),
                "travel_days",    req.getTravelDays(),
                "departure_month", req.getDepartureMonth()
        );

        Map<String, Object> res = callFlask(GOAL_BASE + "/travel", body);
        Map<String, Object> budget = cast(res.get("budget"));

        return TravelBudgetResponseDto.builder()
                .budget(TravelBudgetResponseDto.TravelBudget.builder()
                        .accommodation(toLong(budget.get("accommodation")))
                        .flight(toLong(budget.get("flight")))
                        .food(toLong(budget.get("food")))
                        .transportation(toLong(budget.get("transportation")))
                        .sightseeing(toLong(budget.get("sightseeing")))
                        .total(toLong(budget.get("total")))
                        .build())
                .build();
    }

    // ──────────────────────────────────────
    // [STEP 2] 구매 후보 조회
    // ──────────────────────────────────────
    public PurchaseResponseDto searchPurchase(UUID userId, PurchaseRequestDto req) {
        Map<String, Object> body = Map.of(
                "user_id",   userId.toString(),
                "deadline",  req.getDeadline().toString(),
                "item_name", req.getItemName()
        );

        Map<String, Object> res = callFlask(GOAL_BASE + "/purchase", body);

        List<Map<String, Object>> rawList = (List<Map<String, Object>>) res.get("candidates");
        List<PurchaseResponseDto.Candidate> candidates = rawList.stream()
                .map(c -> PurchaseResponseDto.Candidate.builder()
                        .productName((String) c.get("product_name"))
                        .estimatedPrice(toLong(c.get("estimated_price")))
                        .description((String) c.get("description"))
                        .build())
                .toList();

        return PurchaseResponseDto.builder()
                .itemName((String) res.get("item_name"))
                .candidates(candidates)
                .build();
    }

    // ──────────────────────────────────────
    // [STEP 4] 포트폴리오 비율 추천
    // ──────────────────────────────────────
    public PortfolioResponseDto createPortfolio(UUID userId, PortfolioRequestDto req) {
        Map<String, Object> body = Map.of(
                "user_id",         userId.toString(),
                "deadline",        req.getDeadline().toString(),
                "initial_capital", req.getInitialCapital(),
                "monthly_seed",    req.getMonthlySeed(),
                "target_amount",   req.getTargetAmount()
        );

        Map<String, Object> res = callFlask(GOAL_BASE + "/portfolio", body);
        Map<String, Object> comp = cast(res.get("portfolio_composition"));

        return PortfolioResponseDto.builder()
                .portfolioDetail((String) res.get("portfolio_detail"))
                .portfolioComposition(PortfolioResponseDto.PortfolioComposition.builder()
                        .cashPct(toFloat(comp.get("cash_pct")))
                        .stocksEtfPct(toFloat(comp.get("stocks_etf_pct")))
                        .bondsPct(toFloat(comp.get("bonds_pct")))
                        .build())
                .build();
    }

    // ──────────────────────────────────────
    // [STEP 6] AI 심층 진단
    // ──────────────────────────────────────
    public AnalysisResponseDto analyzePortfolio(UUID userId, AnalysisRequestDto req) {
        Map<String, Object> body = Map.of(
                "user_id", userId.toString(),
                "portfolio_user", Map.of(
                        "cash_ratio",  req.getPortfolioUser().getCashRatio(),
                        "stock_ratio", req.getPortfolioUser().getStockRatio(),
                        "bond_ratio",  req.getPortfolioUser().getBondRatio()
                )
        );

        Map<String, Object> res = callFlask(GOAL_BASE + "/analysis", body);

        return AnalysisResponseDto.builder()
                .analysisReport((String) res.get("analysis_report"))
                .summary((String) res.get("summary"))
                .build();
    }

    @Transactional
    public void confirmGoal(UUID userId, GoalConfirmRequestDto req) {

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        Assets sourceAsset = assetRepository.findById(req.getSourceAssetId())
                .orElseThrow(() -> new IllegalArgumentException("계좌 없음"));

        // 1. goals 저장
        Event goal = Event.builder()
                .user(user)
                .sourceAsset(sourceAsset)
                .eventType(Event.EventType.valueOf(req.getGoalType()))
                .title(req.getTitle())
                .targetAmount(req.getTargetAmount())
                .initialAmount(req.getInitialCapital())
                .currentAmount(req.getInitialCapital()) // 초기 자본금으로 시작
                .durationMonths(calculateMonths(req.getDeadline()))
                .deadline(req.getDeadline())
                .status(Event.EventStatus.ACTIVE)
                .build();

        Event savedGoal = eventRepository.save(goal);

        // 2. goals_portfolios 저장 (비율만 저장, 계좌 연동은 nullable)
        List<EventPortfolios> portfolios = List.of(
                buildPortfolio(savedGoal, "STOCK", req.getStockRatio(), req.getStockAssetId()),
                buildPortfolio(savedGoal, "BOND",  req.getBondRatio(),  req.getBondAssetId()),
                buildPortfolio(savedGoal, "DEPOSIT", req.getCashRatio(), req.getDepositAssetId())
        );
        eventPortfoliosRepository.saveAll(portfolios);

        // 3. spending_budgets 저장 (STEP 4 예산)
        if (req.getBudgets() != null) {
            LocalDate now = LocalDate.now();
            List<SpendingBudgets> budgets = req.getBudgets().stream()
                    .map(b -> SpendingBudgets.builder()
                            .user(user)
                            .event(savedGoal)
                            .category(b.getCategory())
                            .amount(b.getAmount())
                            .ratio(b.getRatio())
                            .year(now.getYear())
                            .month(now.getMonthValue())
                            .build())
                    .toList();
            spendingBudgetRepository.saveAll(budgets);
        }
    }

    private EventPortfolios buildPortfolio(Event goal, String type,
                                           Integer ratio, UUID assetId) {
        Assets asset = (assetId != null)
                ? assetRepository.findById(assetId).orElse(null)
                : null;

        return EventPortfolios.builder()
                .event(goal)
                .productType(EventPortfolios.ProductType.valueOf(type))
                .productRatio(ratio)
                .asset(asset)
                .build();
    }

    private Integer calculateMonths(LocalDate deadline) {
        LocalDate now = LocalDate.now();
        return (int) ChronoUnit.MONTHS.between(now, deadline);
    }

    // ──────────────────────────────────────
    // 공통 Flask 호출
    // ──────────────────────────────────────
    @SuppressWarnings("unchecked")
    private Map<String, Object> callFlask(String path, Map<String, Object> body) {
        try {
            Map<String, Object> response = webClient.post()
                    .uri(flaskMlUrl + path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                throw new IllegalStateException("Flask 서버 응답이 없습니다.");
            }
            return response;

        } catch (Exception e) {
            log.error("[GoalAgent] Flask 호출 실패 — path: {}, 사유: {}", path, e.getMessage());
            throw new IllegalStateException("AI 서버 호출 실패: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object obj) {
        return (Map<String, Object>) obj;
    }

    private Long toLong(Object v) {
        if (v == null) return 0L;
        return Long.valueOf(v.toString());
    }

    private Float toFloat(Object v) {
        if (v == null) return 0f;
        return Float.valueOf(v.toString());
    }
}