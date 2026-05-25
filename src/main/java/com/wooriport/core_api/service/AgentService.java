package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.agent.*;
import com.wooriport.core_api.base.dto.user.PortiSurveyRequestDto;
import com.wooriport.core_api.domain.*;
import com.wooriport.core_api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final UsersService usersService;
    private final PortfolioRepository portfolioRepository;

    private final WebClient webClient;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

    private static final String BASE = "/portfolio";


    // 고정 지출 카테고리 (transaction.category 기준)
    private static final List<String> FIXED_CATEGORIES = List.of("통신", "공과금", "보험료");

    // ──────────────────────────────────────
    // POST /agent/profile
    // ──────────────────────────────────────
    @Transactional
    public AgentProfileResponseDto generateProfile(UUID userId, AgentProfileRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // ──────────────────────────────────────
        // STEP 1. porTI 계산 및 저장
        // ──────────────────────────────────────
        PortiSurveyRequestDto portiRequest = new PortiSurveyRequestDto(request.getAnswers());
        var portiResult = usersService.calculateAndSave(userId, portiRequest);

        // ──────────────────────────────────────
        // STEP 2. 3개월 카테고리별 소비 집계
        // ──────────────────────────────────────
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        List<Object[]> rawExpenses = transactionRepository
                .findCategoryExpenseAvg(userId, threeMonthsAgo);

        // 고정비 제외한 변동 지출만
        List<AgentProfileResponseDto.CategoryExpenseItem> categoryExpense = new ArrayList<>();
        long totalVariable = 0L;

        for (Object[] row : rawExpenses) {
            String category = (String) row[0];
            Long monthlyAvg = ((Number) row[1]).longValue();

            if (!FIXED_CATEGORIES.contains(category)) {
                totalVariable += monthlyAvg;
                categoryExpense.add(AgentProfileResponseDto.CategoryExpenseItem.builder()
                        .name(category)
                        .amount(monthlyAvg)
                        .ratio(0)  // 비율은 아래서 계산
                        .build());
            }
        }

        // 비율 계산
        final long totalFinal = totalVariable;
        categoryExpense = categoryExpense.stream()
                .map(c -> AgentProfileResponseDto.CategoryExpenseItem.builder()
                        .name(c.getName())
                        .amount(c.getAmount())
                        .ratio(totalFinal > 0 ? (int)(c.getAmount() * 100 / totalFinal) : 0)
                        .build())
                .sorted(Comparator.comparingInt(
                        AgentProfileResponseDto.CategoryExpenseItem::getRatio).reversed())
                .collect(Collectors.toList());

        // ──────────────────────────────────────
        // STEP 3. 고정 지출 집계
        // ──────────────────────────────────────
        List<AgentProfileResponseDto.FixedExpenseItem> fixedExpense = new ArrayList<>();
        long totalFixed = 0L;

        for (Object[] row : rawExpenses) {
            String category = (String) row[0];
            Long monthlyAvg = ((Number) row[1]).longValue();

            if (FIXED_CATEGORIES.contains(category)) {
                totalFixed += monthlyAvg;
                fixedExpense.add(AgentProfileResponseDto.FixedExpenseItem.builder()
                        .name(category)
                        .amount(monthlyAvg)
                        .build());
            }
        }

        // ──────────────────────────────────────
        // STEP 4. 투자 성향 (assets 기준)
        // ──────────────────────────────────────
        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);

        long totalBalance = assets.stream().mapToLong(Assets::getBalance).sum();

        // 위험 자산: STOCK
        // 안전 자산: SAVINGS, DEPOSIT, PARKING, CMA, IRP
        long riskBalance = assets.stream()
                .filter(a -> a.getAssetType() == Assets.AccountType.STOCK)
                .mapToLong(Assets::getBalance).sum();

        long safeBalance = totalBalance - riskBalance;

        int riskRatio = totalBalance > 0 ? (int)(riskBalance * 100 / totalBalance) : 0;
        int safeRatio  = 100 - riskRatio;

        AgentProfileResponseDto.InvestTendency investTendency =
                AgentProfileResponseDto.InvestTendency.builder()
                        .safeRatio(safeRatio)
                        .riskRatio(riskRatio)
                        .safeAssets("예적금, 채권")
                        .riskAssets("국내외 주식, 코인")
                        .build();

        // ──────────────────────────────────────
        // STEP 5. 저축 목록 (assets 유형별 잔액)
        // ──────────────────────────────────────
        Map<String, Long> savingsMap = new LinkedHashMap<>();
        savingsMap.put("입출금/CMA", 0L);
        savingsMap.put("예금/적금",  0L);
        savingsMap.put("주택청약",   0L);

        for (Assets asset : assets) {
            switch (asset.getAssetType()) {
                case CHECKING, PARKING, CMA -> savingsMap.merge("입출금/CMA", asset.getBalance(), Long::sum);
                case SAVINGS, DEPOSIT       -> savingsMap.merge("예금/적금",  asset.getBalance(), Long::sum);
                // 주택청약은 account_purpose로 구분
                default -> {
                    if (asset.getAccountPurpose() != null
                            && asset.getAccountPurpose().contains("청약")) {
                        savingsMap.merge("주택청약", asset.getBalance(), Long::sum);
                    }
                }
            }
        }

        long totalSavings = savingsMap.values().stream().mapToLong(Long::longValue).sum();

        List<AgentProfileResponseDto.SavingsItem> savingsList = savingsMap.entrySet().stream()
                .map(e -> AgentProfileResponseDto.SavingsItem.builder()
                        .type(e.getKey())
                        .amount(e.getValue())
                        .ratio(totalSavings > 0 ? (int)(e.getValue() * 100 / totalSavings) : 0)
                        .build())
                .collect(Collectors.toList());

        // ──────────────────────────────────────
        // STEP 6. FastAPI /profile 호출
        // ──────────────────────────────────────
        Map<String, Object> flaskBody = new HashMap<>();
        flaskBody.put("user_id", userId.toString());
        flaskBody.put("porti_type", portiResult.getPortiType().name());
        flaskBody.put("porti_comment", user.getPortiComment());
        flaskBody.put("category_expense", categoryExpense.stream()
                .map(c -> Map.of("name", c.getName(), "expense", c.getAmount()))
                .collect(Collectors.toList()));
        flaskBody.put("assets", assets.stream()
                .map(a -> Map.of(
                        "asset_type", a.getAssetType().name(),
                        "balance", a.getBalance()))
                .collect(Collectors.toList()));

        Map<String, Object> flaskResponse = callFlask("/profile", flaskBody);

        // ──────────────────────────────────────
        // STEP 7. 응답 조합
        // ──────────────────────────────────────
        return AgentProfileResponseDto.builder()
                .portiType(portiResult.getPortiType().name())
                .portiTypeName(portiResult.getTypeName())
                .portiDescription(portiResult.getDescription())
                .monthlyAvgExpense(totalVariable + totalFixed)
                .categoryExpense(categoryExpense)
                .fixedExpense(fixedExpense)
                .totalFixedExpense(totalFixed)
                .investTendency(investTendency)
                .savingsList(savingsList)
                .expenseComment((String) flaskResponse.get("expense_comment"))
                .investComment((String) flaskResponse.get("invest_comment"))
                .savingsComment((String) flaskResponse.get("savings_comment"))
                .build();
    }


    // ──────────────────────────────────────
    // POST /agent/recommend
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public AgentRecommendResponseDto recommend(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. porTI 확인
        if (user.getPortiType() == null) {
            throw new IllegalStateException("porTI 검사를 먼저 완료해주세요.");
        }

        // 2. 최근 급여 조회
        Transactions salaryTx = transactionRepository
                .findLatestSalaryTransaction(userId)
                .orElseThrow(() -> new IllegalStateException("급여 트랜잭션이 없습니다."));
        Long salary = salaryTx.getAmount();

        // 3. 3개월 카테고리별 소비 집계
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        List<Object[]> rawExpenses = transactionRepository
                .findCategoryExpenseAvg(userId, threeMonthsAgo);

        // 변동 지출
        List<Map<String, Object>> categoryExpense = new ArrayList<>();
        // 고정 지출 합계
        long totalFixed = 0L;

        for (Object[] row : rawExpenses) {
            String category = (String) row[0];
            Long monthlyAvg = ((Number) row[1]).longValue();

            if (FIXED_CATEGORIES.contains(category)) {
                totalFixed += monthlyAvg;
            } else {
                categoryExpense.add(Map.of(
                        "name", category,
                        "expense", monthlyAvg));
            }
        }

        // 4. 보유 계좌 조회
        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);

        List<Map<String, Object>> assetList = assets.stream()
                .map(a -> Map.<String, Object>of(
                        "asset_type", a.getAssetType().name(),
                        "balance", a.getBalance()))
                .collect(Collectors.toList());

        // 5. porTI 코멘트 (유형 설명)
        String portiComment = user.getPortiComment();

        // 6. FastAPI /rebalance 호출
        Map<String, Object> flaskBody = new HashMap<>();
        flaskBody.put("user_id", userId.toString());
        flaskBody.put("porti_type", user.getPortiType().name());
        flaskBody.put("porti_comment", portiComment);
        flaskBody.put("category_expense", categoryExpense);
        flaskBody.put("assets", assetList);
        flaskBody.put("fixed_expense", totalFixed);
        flaskBody.put("salary", salary);

        Map<String, Object> flaskResponse = callFlask("/rebalance", flaskBody);

        // 7. Flask 응답 파싱
        Long investAmount = ((Number) flaskResponse.get("invest_amount")).longValue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawPlans =
                (List<Map<String, Object>>) flaskResponse.get("salary_rebalance");

        // asset_number로 계좌 매핑
        Map<String, Assets> assetNumberMap = assets.stream()
                .filter(a -> a.getAssetNumber() != null)
                .collect(Collectors.toMap(Assets::getAssetNumber, a -> a, (a, b) -> a));

        List<AgentRecommendResponseDto.RebalancingPlan> plans = rawPlans.stream()
                .map(p -> {
                    String assetNumber = (String) p.get("asset_number");
                    String category   = (String) p.get("category");
                    Long amount = p.get("ratio") != null
                            ? salary * ((Number) p.get("ratio")).longValue() / 100
                            : 0L;

                    Assets matched = assetNumberMap.get(assetNumber);

                    return AgentRecommendResponseDto.RebalancingPlan.builder()
                            .assetId(matched != null ? matched.getId() : null)
                            .institution(matched != null ? matched.getInstitution() : null)
                            .assetType(matched != null ? matched.getAssetType().name() : null)
                            .assetNumber(assetNumber)
                            .amount(amount)
                            .nickname(category)   // Flask가 지은 별명
                            .build();
                })
                .collect(Collectors.toList());

        // 8. 남은 금액 계산
        Long totalPlanned = plans.stream().mapToLong(AgentRecommendResponseDto.RebalancingPlan::getAmount).sum();
        Long remainingAmount = salary - investAmount - totalPlanned;

        log.info("[AgentService] 리밸런싱 추천 완료 — userId: {}, 급여: {}원, 투자: {}원, 이체: {}건",
                userId, salary, investAmount, plans.size());

        return AgentRecommendResponseDto.builder()
                .salary(salary)
                .investAmount(investAmount)
                .totalFixedExpense(totalFixed)
                .fixedExpenseComment(String.format(
                        "고정 지출 %,d원은 먼저 빠졌어요. 변동을 원하시면 수동 조정이 가능해요.",
                        totalFixed))
                .rebalancingPlans(plans)
                .remainingAmount(remainingAmount)
                .build();
    }


    // ──────────────────────────────────────
    // POST /agent/input
    // 자연어 이벤트 → 리밸런싱 재추천 + diff
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public AgentInputResponseDto input(UUID userId, AgentInputRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 현재 portfolios 조회 (기존 리밸런싱 계획)
        List<Portfolios> currentPortfolios = portfolioRepository.findByUserId(userId);
        if (currentPortfolios.isEmpty()) {
            throw new IllegalStateException("리밸런싱 설정을 먼저 완료해주세요.");
        }

        // 2. 현재 투자 금액
        Long currentInvestAmount = user.getMonthlyInvestAmount() != null
                ? user.getMonthlyInvestAmount() : 0L;

        // 3. 급여 조회
        Transactions salaryTx = transactionRepository
                .findLatestSalaryTransaction(userId)
                .orElseThrow(() -> new IllegalStateException("급여 트랜잭션이 없습니다."));
        Long salary = salaryTx.getAmount();

        // 4. 기존 포트폴리오 Map 생성 (assetId → amount, diff 계산용)
        Map<UUID, Long> previousMap = currentPortfolios.stream()
                .filter(p -> p.getAsset() != null)
                .collect(Collectors.toMap(
                        p -> p.getAsset().getId(),
                        Portfolios::getAssetAmount,
                        (a, b) -> a));

        // 5. FastAPI /input 호출
        List<Map<String, Object>> currentPortfolioList = currentPortfolios.stream()
                .filter(p -> p.getAsset() != null)
                .map(p -> Map.<String, Object>of(
                        "asset_id",    p.getAsset().getId().toString(),
                        "institution", p.getAsset().getInstitution(),
                        "amount",      p.getAssetAmount(),
                        "nickname",    p.getAssetType().name()))
                .collect(Collectors.toList());

        Map<String, Object> flaskBody = new HashMap<>();
        flaskBody.put("user_id",           userId.toString());
        flaskBody.put("user_input",        request.getUserInput());
        flaskBody.put("porti_type",        user.getPortiType().name());
        flaskBody.put("porti_comment",     user.getPortiComment());
        flaskBody.put("current_portfolio", currentPortfolioList);
        flaskBody.put("invest_amount",     currentInvestAmount);
        flaskBody.put("salary",            salary);

        Map<String, Object> flaskResponse = callFlask("/input", flaskBody);

        // 6. Flask 응답 파싱
        Long newInvestAmount = ((Number) flaskResponse.get("invest_amount")).longValue();
        Long investAmountDiff = newInvestAmount - currentInvestAmount;

        @SuppressWarnings("unchecked")
        Map<String, Object> rawEvent = (Map<String, Object>) flaskResponse.get("event");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawPlans =
                (List<Map<String, Object>>) flaskResponse.get("rebalancing_plans");

        // 7. 계좌 조회 (매핑용)
        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);
        Map<String, Assets> assetIdMap = assets.stream()
                .collect(Collectors.toMap(
                        a -> a.getId().toString(), a -> a, (a, b) -> a));

        // 8. diff 계산 후 plans 생성
        List<AgentInputResponseDto.RebalancingPlan> plans = rawPlans.stream()
                .map(p -> {
                    String assetIdStr = (String) p.get("asset_id");
                    Long amount       = ((Number) p.get("amount")).longValue();
                    String nickname   = (String) p.get("nickname");

                    Assets matched = assetIdStr != null ? assetIdMap.get(assetIdStr) : null;
                    UUID assetUUID = matched != null ? matched.getId() : null;
                    Long previous  = assetUUID != null ? previousMap.getOrDefault(assetUUID, 0L) : 0L;

                    return AgentInputResponseDto.RebalancingPlan.builder()
                            .assetId(assetUUID)
                            .institution(matched != null ? matched.getInstitution() : null)
                            .assetType(matched != null ? matched.getAssetType().name() : null)
                            .assetNumber(matched != null ? matched.getAssetNumber() : null)
                            .amount(amount)
                            .nickname(nickname)
                            .previousAmount(previous)
                            .diff(amount - previous)
                            .build();
                })
                .collect(Collectors.toList());

        Long totalPlanned = plans.stream()
                .mapToLong(AgentInputResponseDto.RebalancingPlan::getAmount).sum();
        Long remainingAmount = salary - newInvestAmount - totalPlanned;

        log.info("[AgentService] input 완료 — userId: {}, 입력: {}", userId, request.getUserInput());

        return AgentInputResponseDto.builder()
                .event(AgentInputResponseDto.EventSummary.builder()
                        .title((String) rawEvent.get("title"))
                        .targetAmount(toLong(rawEvent.get("target_amount")))
                        .monthlySaving(toLong(rawEvent.get("monthly_saving")))
                        .deadline((String) rawEvent.get("deadline"))
                        .build())
                .investAmount(newInvestAmount)
                .investAmountDiff(investAmountDiff)
                .rebalancingPlans(plans)
                .remainingAmount(remainingAmount)
                .build();
    }



//    // ──────────────────────────────────────
//    // [STEP 6] AI 심층 진단
//    // ──────────────────────────────────────
//    public AnalysisResponseDto analyzePortfolio(UUID userId, AnalysisRequestDto req) {
//        Map<String, Object> body = Map.of(
//                "user_id", userId.toString(),
//                "portfolio_user", Map.of(
//                        "cash_ratio",  req.getPortfolioUser().getCashRatio(),
//                        "stock_ratio", req.getPortfolioUser().getStockRatio(),
//                        "bond_ratio",  req.getPortfolioUser().getBondRatio()
//                )
//        );
//
//        Map<String, Object> res = callFlask(BASE + "/analysis", body);
//
//        return AnalysisResponseDto.builder()
//                .analysisReport((String) res.get("analysis_report"))
//                .summary((String) res.get("summary"))
//                .build();
//    }
//
//    @Transactional
//    public void confirmGoal(UUID userId, GoalConfirmRequestDto req) {
//
//        Users user = userRepository.findById(userId)
//                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
//
//        Assets sourceAsset = assetRepository.findById(req.getSourceAssetId())
//                .orElseThrow(() -> new IllegalArgumentException("계좌 없음"));
//
//        // 1. goals 저장
//        Event goal = Event.builder()
//                .user(user)
//                .sourceAsset(sourceAsset)
//                .eventType(Event.EventType.valueOf(req.getGoalType()))
//                .title(req.getTitle())
//                .targetAmount(req.getTargetAmount())
//                .initialAmount(req.getInitialCapital())
//                .currentAmount(req.getInitialCapital()) // 초기 자본금으로 시작
//                .durationMonths(calculateMonths(req.getDeadline()))
//                .deadline(req.getDeadline())
//                .status(Event.EventStatus.ACTIVE)
//                .build();
//
//        Event savedGoal = eventRepository.save(goal);
//
//        // 2. goals_portfolios 저장 (비율만 저장, 계좌 연동은 nullable)
//        List<PortfolioItems> portfolios = List.of(
//                buildPortfolio(savedGoal, "STOCK", req.getStockRatio(), req.getStockAssetId()),
//                buildPortfolio(savedGoal, "BOND",  req.getBondRatio(),  req.getBondAssetId()),
//                buildPortfolio(savedGoal, "DEPOSIT", req.getCashRatio(), req.getDepositAssetId())
//        );
//        portfolioItemRepository.saveAll(portfolios);
//
//        // 3. spending_budgets 저장 (STEP 4 예산)
//        if (req.getBudgets() != null) {
//            LocalDate now = LocalDate.now();
//            List<SpendingBudgets> budgets = req.getBudgets().stream()
//                    .map(b -> SpendingBudgets.builder()
//                            .user(user)
//                            .event(savedGoal)
//                            .category(b.getCategory())
//                            .amount(b.getAmount())
//                            .ratio(b.getRatio())
//                            .year(now.getYear())
//                            .month(now.getMonthValue())
//                            .build())
//                    .toList();
//            spendingBudgetRepository.saveAll(budgets);
//        }
//    }
//
//    private PortfolioItems buildPortfolio(Event goal, String type,
//                                          Integer ratio, UUID assetId) {
//        Assets asset = (assetId != null)
//                ? assetRepository.findById(assetId).orElse(null)
//                : null;
//
//        return PortfolioItems.builder()
//                .event(goal)
//                .productType(PortfolioItems.ProductType.valueOf(type))
//                .productRatio(ratio)
//                .asset(asset)
//                .build();
//    }
//
//    private Integer calculateMonths(LocalDate deadline) {
//        LocalDate now = LocalDate.now();
//        return (int) ChronoUnit.MONTHS.between(now, deadline);
//    }

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