package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.agent.*;
import com.wooriport.core_api.base.dto.user.PortiSurveyRequestDto;
import com.wooriport.core_api.base.exception.PortfolioNotSetException;
import com.wooriport.core_api.base.exception.SalaryNotFoundException;
import com.wooriport.core_api.base.exception.UserNotFoundException;
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
    private final ProductRepository productRepository;
    private final PortfolioFlowRepository portfolioFlowRepository;
    private final PortfolioFlowItemRepository portfolioFlowItemRepository;
    private final EventRepository eventRepository;

    private final WebClient webClient;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

    // 고정 지출 카테고리 (transaction.category 기준)
    private static final List<String> FIXED_CATEGORIES = List.of("통신", "공과금", "보험료");

    // ──────────────────────────────────────
    // POST /agent/profile
    // ──────────────────────────────────────
    @Transactional
    public AgentProfileResponseDto generateProfile(UUID userId, AgentProfileRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        // ──────────────────────────────────────
        // STEP 1. porTI 계산 및 저장
        // ──────────────────────────────────────
        PortiSurveyRequestDto portiRequest = new PortiSurveyRequestDto(request.getAnswers());
        var portiResult = usersService.calculateAndSave(userId, portiRequest);

        if (request.getStockThemes() != null) user.updateStockThemes(request.getStockThemes());
        if (request.getLifeGoal() != null) user.updateLifeGoal(request.getLifeGoal());

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
                        "asset_id",    a.getId().toString(),
                        "account_name", a.getAccountName() != null ? a.getAccountName() : "",
                        "asset_type",  a.getAssetType().name(),
                        "balance",     a.getBalance()))
                .collect(Collectors.toList()));

        Map<String, Object> flaskResponse = callFlask("/portfolio/profile", flaskBody);

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
                .orElseThrow(() -> new UserNotFoundException());

        // 1. porTI 확인
        if (user.getPortiType() == null) {
            throw new PortfolioNotSetException();
        }

        // 2. 급여 조회 (users.salary 우선)
        Long salary = user.getSalary();
        if (salary == null) throw new SalaryNotFoundException();

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
                        "asset_id",    a.getId().toString(),
                        "account_name", a.getAccountName() != null ? a.getAccountName() : "",
                        "asset_type",  a.getAssetType().name(),
                        "balance",     a.getBalance()))
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

        Map<String, Object> flaskResponse = callFlask("/portfolio/rebalance", flaskBody);

        // 7. Flask 응답 파싱
        Long investAmount = toLong(flaskResponse.get("invest_amount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawPlans =
                (List<Map<String, Object>>) flaskResponse.get("salary_rebalance");
        if (rawPlans == null) {
            throw new IllegalStateException("Flask 응답에 salary_rebalance 필드가 없습니다.");
        }

        // asset_id로 계좌 매핑
        Map<String, Assets> assetIdMap = assets.stream()
                .collect(Collectors.toMap(a -> a.getId().toString(), a -> a));

        List<AgentRecommendResponseDto.RebalancingPlan> plans = rawPlans.stream()
                .map(p -> {
                    String assetId  = (String) p.get("asset_id");
                    String category = (String) p.get("category");
                    Long amount = p.get("ratio") != null
                            ? salary * ((Number) p.get("ratio")).longValue() / 100
                            : 0L;

                    Assets matched = assetIdMap.get(assetId);

                    return AgentRecommendResponseDto.RebalancingPlan.builder()
                            .assetId(matched != null ? matched.getId() : null)
                            .institution(matched != null ? matched.getInstitution() : null)
                            .assetType(matched != null ? matched.getAssetType().name() : null)
                            .assetNumber(matched != null ? matched.getAssetNumber() : null)
                            .amount(amount)
                            .nickname(category)
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
    // POST /agent/event/input
    // 자연어 목표 입력 → AI가 목표 구체화
    // ──────────────────────────────────────
    public AgentGoalResponseDto goal(UUID userId, AgentGoalRequestDto request) {
        Map<String, Object> flaskBody = new HashMap<>();
        flaskBody.put("user_id", userId.toString());
        flaskBody.put("user_input", request.getUserInput());

        Map<String, Object> flaskResponse = callFlask("/event/input", flaskBody);

        return AgentGoalResponseDto.builder()
                .createdAt(String.valueOf(flaskResponse.get("created_at")))
                .title(String.valueOf(flaskResponse.get("title")))
                .targetAmount(String.valueOf(flaskResponse.get("target_amount")))
                .deadline(String.valueOf(flaskResponse.get("deadline")))
                .build();
    }


    // ──────────────────────────────────────
    // POST /agent/event/rebalacne
    // 구체화된 목표 → 리밸런싱 재추천 + diff
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public AgentInputResponseDto rebalance(UUID userId, AgentInputRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        // 1. 현재 portfolios 조회
        List<Portfolios> currentPortfolios = portfolioRepository.findByUserId(userId);
        if (currentPortfolios.isEmpty()) {
            throw new IllegalStateException("리밸런싱 설정을 먼저 완료해주세요.");
        }

        // 2. 급여 조회 (users.salary 우선)
        Long salary = user.getSalary();
        if (salary == null) throw new SalaryNotFoundException();

        // 3. 현재 투자 금액
        Long currentInvestAmount = user.getMonthlyInvestAmount() != null
                ? user.getMonthlyInvestAmount() : 0L;

        // 4. diff 계산용 기존 포트폴리오 Map (assetId → amount)
        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);
        Map<String, Assets> assetIdMap = assets.stream()
                .collect(Collectors.toMap(a -> a.getId().toString(), a -> a));

        Map<String, Long> previousAmountMap = currentPortfolios.stream()
                .filter(p -> p.getAsset() != null)
                .collect(Collectors.toMap(
                        p -> p.getAsset().getId().toString(),
                        Portfolios::getAssetAmount,
                        (a, b) -> a));

        // 5. 현재 salary_rebalance 목록 (amount 기반)
        List<Map<String, Object>> currentSalaryRebalance = currentPortfolios.stream()
                .filter(p -> p.getAsset() != null)
                .map(p -> Map.<String, Object>of(
                        "asset_id",    p.getAsset().getId().toString(),
                        "account_name", p.getAsset().getAccountName() != null ? p.getAsset().getAccountName() : "",
                        "amount",      p.getAssetAmount()))
                .collect(Collectors.toList());

        // 6. Flask /rebalance 호출
        Map<String, Object> rebalanceObj = new HashMap<>();
        rebalanceObj.put("salary",           salary);
        rebalanceObj.put("invest_amount",    currentInvestAmount);
        rebalanceObj.put("salary_rebalance", currentSalaryRebalance);

        Map<String, Object> flaskBody = new HashMap<>();
        flaskBody.put("user_id",       userId.toString());
        flaskBody.put("title",         request.getTitle());
        flaskBody.put("target_amount", request.getTargetAmount());
        flaskBody.put("deadline",      request.getDeadline());
        flaskBody.put("porti_type",    user.getPortiType().name());
        flaskBody.put("porti_comment", user.getPortiComment());
        flaskBody.put("rebalance",     rebalanceObj);

        Map<String, Object> flaskResponse = callFlask("/event/rebalance", flaskBody);

        // 7. Flask 응답 파싱
        Long newInvestAmount = toLong(flaskResponse.get("invest_amount"));
        String rebalanceComment = (String) flaskResponse.get("rebalance_comment");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawPlans =
                (List<Map<String, Object>>) flaskResponse.get("salary_rebalance");
        if (rawPlans == null) {
            throw new IllegalStateException("Flask 응답에 salary_rebalance 필드가 없습니다.");
        }

        // 8. asset_id 기반으로 diff 계산 (amount 직접 사용)
        List<AgentInputResponseDto.RebalancingPlan> plans = rawPlans.stream()
                .map(p -> {
                    String assetId  = (String) p.get("asset_id");
                    Long   amount   = ((Number) p.get("amount")).longValue();
                    String category = (String) p.get("category");

                    Assets matched  = assetIdMap.get(assetId);
                    Long   previous = previousAmountMap.getOrDefault(assetId, 0L);

                    return AgentInputResponseDto.RebalancingPlan.builder()
                            .assetId(matched != null ? matched.getId() : null)
                            .institution(matched != null ? matched.getInstitution() : null)
                            .assetType(matched != null ? matched.getAssetType().name() : null)
                            .assetNumber(matched != null ? matched.getAssetNumber() : null)
                            .amount(amount)
                            .nickname(category)
                            .previousAmount(previous)
                            .diff(amount - previous)
                            .build();
                })
                .collect(Collectors.toList());

        // 9. 남은 금액 계산
        Long totalPlanned    = plans.stream().mapToLong(AgentInputResponseDto.RebalancingPlan::getAmount).sum();
        Long remainingAmount = salary - newInvestAmount - totalPlanned;

        log.info("[AgentService] input 완료 — userId: {}, 목표: {}", userId, request.getTitle());

        return AgentInputResponseDto.builder()
                .rebalanceComment(rebalanceComment)
                .investAmount(newInvestAmount)
                .investAmountDiff(newInvestAmount - currentInvestAmount)
                .rebalancingPlans(plans)
                .remainingAmount(remainingAmount)
                .build();
    }

    // ──────────────────────────────────────
    // POST /agent/prescriptions
    // PrescriptionComplete 화면 진입 시 호출 — FastAPI /asset-portfolio 로
    // AI 포트폴리오 생성 요청 후 portfolio_flows + items 저장
    // ──────────────────────────────────────
    @Transactional
    public void generatePrescriptions(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        // 1. 보유 자산 — 카드 + 월급 리밸런싱에 이미 묶인 계좌 제외
        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);

        Set<UUID> rebalancedAssetIds = portfolioRepository.findByUserId(userId).stream()
                .map(Portfolios::getAsset)
                .filter(Objects::nonNull)
                .map(Assets::getId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> investAssets = assets.stream()
                .filter(a -> a.getAssetType() != Assets.AccountType.CREDIT_CARD
                          && a.getAssetType() != Assets.AccountType.DEBIT_CARD)
                .filter(a -> !rebalancedAssetIds.contains(a.getId()))
                .map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("asset_type", a.getAssetType() != null ? a.getAssetType().name() : null);
                    m.put("account_name", a.getAccountName());
                    m.put("asset_id", a.getId().toString());
                    m.put("balance", a.getBalance());
                    return m;
                })
                .collect(Collectors.toList());

        // 2. 상품 카탈로그 — 응답 portfolio[].name → product 매핑용 (요청 본문엔 더 이상 안 보냄)
        List<Products> productList = productRepository.findAllActive();

        // 3. FastAPI /asset-portfolio 호출
        Map<String, Object> flaskBody = new HashMap<>();
        flaskBody.put("user_id", userId.toString());
        flaskBody.put("invest_amount",
                user.getMonthlyInvestAmount() != null ? user.getMonthlyInvestAmount() : 0L);
        flaskBody.put("interest", user.getLifeGoal());              // 관심사 (결혼/차/집 등)
        flaskBody.put("invest_interests", user.getStockThemes());   // 관심 주식 테마 (최대 3개)
        flaskBody.put("porti_type", user.getPortiType() != null ? user.getPortiType().name() : null);
        flaskBody.put("porti_comment", user.getPortiComment());
        flaskBody.put("invest_assets", investAssets);

        Map<String, Object> flaskResponse = callFlask("/portfolio/asset-portfolio", flaskBody);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> investmentFlows =
                (List<Map<String, Object>>) flaskResponse.get("investment_flows");
        if (investmentFlows == null) {
            throw new IllegalStateException("FastAPI 응답에 investment_flows 가 없습니다.");
        }

        // 4. 기존 기본(event=null) 흐름 삭제 — 새 처방전으로 교체
        List<PortfolioFlows> existing = portfolioFlowRepository.findAllByUserIdWithDetails(userId).stream()
                .filter(f -> f.getEvent() == null)
                .collect(Collectors.toList());
        if (!existing.isEmpty()) {
            portfolioFlowRepository.deleteAll(existing);
            portfolioFlowRepository.flush();
        }

        // 5. 매핑 테이블
        Map<UUID, Assets> assetById = assets.stream()
                .collect(Collectors.toMap(Assets::getId, a -> a, (a, b) -> a));
        Map<String, Products> productByName = productList.stream()
                .collect(Collectors.toMap(Products::getName, p -> p, (a, b) -> a));

        // 6. 각 investment_flow 저장 (끌어오기 PULL 제거 — gathering + portfolio(PUT)만 저장)
        for (Map<String, Object> flowDto : investmentFlows) {
            String title = (String) flowDto.get("title");
            String summary = (String) flowDto.get("summary");
            String term = (String) flowDto.get("term");   // 단기/중기/장기 등 원본 그대로
            Long flowAmount = toLong(flowDto.get("amount"));

            // 모을 통장: gathering_id 있으면 보유 계좌 선택, null 이면 계좌 추천(gathering_account 정보 저장)
            Assets gatheringAsset = null;
            Object gatheringIdObj = flowDto.get("gathering_id");
            if (gatheringIdObj != null) {
                gatheringAsset = assetById.get(UUID.fromString(gatheringIdObj.toString()));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> gatheringAccount =
                    (Map<String, Object>) flowDto.get("gathering_account");

            PortfolioFlows flow = PortfolioFlows.builder()
                    .user(user)
                    .event(null)
                    .title(title != null ? title : "")
                    .summary(summary)
                    .term(term)
                    .amount(flowAmount)
                    .gatheringAsset(gatheringAsset)
                    .gatheringName(gatheringAccount != null ? (String) gatheringAccount.get("name") : null)
                    .gatheringType(gatheringAccount != null ? (String) gatheringAccount.get("type") : null)
                    .gatheringInstitution(gatheringAccount != null ? (String) gatheringAccount.get("institution") : null)
                    .gatheringInterestRate(gatheringAccount != null ? toDouble(gatheringAccount.get("interest_rate")) : null)
                    .accountComment((String) flowDto.get("account_comment"))
                    .expectedRrPct(toDouble(flowDto.get("expected_rr_pct")))
                    .investmentMonths(toInteger(flowDto.get("investment_months")))
                    .expectedAmount(toDouble(flowDto.get("expected_amount")))
                    .rrComment((String) flowDto.get("rr_comment"))
                    .isActive(false)
                    .build();
            PortfolioFlows savedFlow = portfolioFlowRepository.save(flow);

            // portfolio → PUT (상품 + 비율 + 코멘트)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> portfolio =
                    (List<Map<String, Object>>) flowDto.get("portfolio");
            if (portfolio != null) {
                for (Map<String, Object> p : portfolio) {
                    String name = (String) p.get("name");
                    Integer ratio = p.get("ratio") != null
                            ? ((Number) p.get("ratio")).intValue() : 0;
                    Products product = name != null ? productByName.get(name) : null;
                    portfolioFlowItemRepository.save(PortfolioFlowItems.builder()
                            .flow(savedFlow)
                            .product(product)
                            .productRatio(ratio)
                            .aiComment((String) p.get("comment"))
                            .build());
                }
            }
        }

        log.info("[AgentService] AI 포트폴리오 생성 완료 — userId={}, flows={}",
                userId, investmentFlows.size());
    }

    // ──────────────────────────────────────
    // POST /agent/event/prescriptions
    // 이벤트 처방전 — 최근 활성 event 기준으로 FastAPI /event/asset-portfolio 호출
    // 응답 후 해당 유저의 모든 기존 흐름 삭제 → 새 흐름을 event에 묶어 저장
    // ──────────────────────────────────────
    @Transactional
    public void generateEventPrescriptions(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        // 1. 최근 활성 이벤트 조회 (별도 API가 미리 저장해뒀다고 가정)
        Event event = eventRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("활성 이벤트가 없습니다."));

        // 2. 보유 자산 — 카드 + 월급 리밸런싱에 묶인 계좌 제외
        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);

        Set<UUID> rebalancedAssetIds = portfolioRepository.findByUserId(userId).stream()
                .map(Portfolios::getAsset)
                .filter(Objects::nonNull)
                .map(Assets::getId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> investAssets = assets.stream()
                .filter(a -> a.getAssetType() != Assets.AccountType.CREDIT_CARD
                          && a.getAssetType() != Assets.AccountType.DEBIT_CARD)
                .filter(a -> !rebalancedAssetIds.contains(a.getId()))
                .map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("asset_type", a.getAssetType() != null ? a.getAssetType().name() : null);
                    m.put("account_name", a.getAccountName());
                    m.put("asset_id", a.getId().toString());
                    m.put("balance", a.getBalance());
                    return m;
                })
                .collect(Collectors.toList());

        // 3. 활성 상품 카탈로그
        List<Products> productList = productRepository.findAllActive();
        List<Map<String, Object>> productsBody = productList.stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("product_type", p.getProductType() != null ? p.getProductType().name() : null);
                    m.put("institution", p.getInstitution());
                    m.put("name", p.getName());
                    m.put("interest_rate", p.getInterestRate());
                    m.put("description", p.getDescription());
                    return m;
                })
                .collect(Collectors.toList());

        // 4. 기존 흐름 조회 (FastAPI에 보낼 investment_flows + 이후 삭제 대상)
        List<PortfolioFlows> existingFlows = portfolioFlowRepository.findAllByUserIdWithDetails(userId);

        List<Map<String, Object>> investmentFlowsBody = existingFlows.stream()
                .map(f -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("title", f.getTitle());
                    m.put("term", f.getTerm());
                    m.put("summary", f.getSummary());

                    List<Map<String, Object>> fundingSources = f.getItems().stream()
                            .filter(PortfolioFlowItems::isPull)
                            .map(i -> {
                                Map<String, Object> src = new HashMap<>();
                                src.put("account_name", i.getAsset() != null ? i.getAsset().getAccountName() : null);
                                src.put("asset_id", i.getAsset() != null ? i.getAsset().getId().toString() : null);
                                return src;
                            })
                            .collect(Collectors.toList());
                    m.put("funding_sources", fundingSources);

                    m.put("gathering_account",
                            f.getGatheringAsset() != null ? f.getGatheringAsset().getId().toString() : null);
                    m.put("amount", f.getAmount());

                    List<Map<String, Object>> portfolio = f.getItems().stream()
                            .filter(PortfolioFlowItems::isPut)
                            .map(i -> {
                                Map<String, Object> p = new HashMap<>();
                                p.put("name", i.getProduct() != null ? i.getProduct().getName() : null);
                                p.put("ratio", i.getProductRatio());
                                return p;
                            })
                            .collect(Collectors.toList());
                    m.put("portfolio", portfolio);

                    return m;
                })
                .collect(Collectors.toList());

        // 5. FastAPI /event/asset-portfolio 호출
        Map<String, Object> flaskBody = new HashMap<>();
        flaskBody.put("user_id", userId.toString());
        flaskBody.put("title", event.getTitle());
        flaskBody.put("target_amount", event.getTargetAmount());
        flaskBody.put("deadline", event.getDeadline().toString());
        flaskBody.put("invest_amount",
                user.getMonthlyInvestAmount() != null ? user.getMonthlyInvestAmount() : 0L);
        flaskBody.put("porti_type", user.getPortiType() != null ? user.getPortiType().name() : null);
        flaskBody.put("porti_comment", user.getPortiComment());
        flaskBody.put("invest_assets", investAssets);
        flaskBody.put("products", productsBody);
        flaskBody.put("investment_flows", investmentFlowsBody);

        Map<String, Object> flaskResponse = callFlask("/event/asset-portfolio", flaskBody);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newInvestmentFlows =
                (List<Map<String, Object>>) flaskResponse.get("investment_flows");
        if (newInvestmentFlows == null) {
            throw new IllegalStateException("FastAPI 응답에 investment_flows 가 없습니다.");
        }

        // 6. 해당 유저의 모든 기존 흐름 삭제
        if (!existingFlows.isEmpty()) {
            portfolioFlowRepository.deleteAll(existingFlows);
            portfolioFlowRepository.flush();
        }

        // 7. 매핑 테이블
        Map<UUID, Assets> assetById = assets.stream()
                .collect(Collectors.toMap(Assets::getId, a -> a, (a, b) -> a));
        Map<String, Products> productByName = productList.stream()
                .collect(Collectors.toMap(Products::getName, p -> p, (a, b) -> a));

        // 8. 새 investment_flows 저장 (event에 묶어서)
        for (Map<String, Object> flowDto : newInvestmentFlows) {
            String title = (String) flowDto.get("title");
            String summary = (String) flowDto.get("summary");
            String term = mapTerm((String) flowDto.get("term"));
            Long flowAmount = flowDto.get("amount") != null
                    ? ((Number) flowDto.get("amount")).longValue() : 0L;

            Assets gatheringAsset = null;
            Object gatheringObj = flowDto.get("gathering_account");
            if (gatheringObj != null) {
                gatheringAsset = assetById.get(UUID.fromString(gatheringObj.toString()));
            }

            PortfolioFlows flow = PortfolioFlows.builder()
                    .user(user)
                    .event(event)
                    .title(title != null ? title : "")
                    .summary(summary)
                    .term(term)
                    .amount(flowAmount)
                    .gatheringAsset(gatheringAsset)
                    .isActive(false)
                    .build();
            PortfolioFlows savedFlow = portfolioFlowRepository.save(flow);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fundingSources =
                    (List<Map<String, Object>>) flowDto.get("funding_sources");
            if (fundingSources != null) {
                for (Map<String, Object> src : fundingSources) {
                    Object assetIdObj = src.get("asset_id");
                    if (assetIdObj == null) continue;
                    UUID srcAssetId = UUID.fromString(assetIdObj.toString());
                    portfolioFlowItemRepository.save(PortfolioFlowItems.builder()
                            .flow(savedFlow)
                            .asset(assetById.get(srcAssetId))
                            .build());
                }
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> portfolio =
                    (List<Map<String, Object>>) flowDto.get("portfolio");
            if (portfolio != null) {
                for (Map<String, Object> p : portfolio) {
                    String name = (String) p.get("name");
                    Integer ratio = p.get("ratio") != null
                            ? ((Number) p.get("ratio")).intValue() : 0;
                    Products product = name != null ? productByName.get(name) : null;
                    portfolioFlowItemRepository.save(PortfolioFlowItems.builder()
                            .flow(savedFlow)
                            .product(product)
                            .productRatio(ratio)
                            .build());
                }
            }
        }

        log.info("[AgentService] 이벤트 AI 포트폴리오 생성 완료 — userId={}, eventId={}, flows={}",
                userId, event.getId(), newInvestmentFlows.size());
    }

    private static String mapTerm(String fullTerm) {
        if (fullTerm == null) return "중";
        if (fullTerm.startsWith("단")) return "단";
        if (fullTerm.startsWith("장")) return "장";
        return "중";
    }

    // FastAPI 응답(JSON number)의 안전한 형 변환 헬퍼 (toLong 은 기존 메서드 재사용)
    private static Double toDouble(Object v) {
        return v != null ? ((Number) v).doubleValue() : null;
    }

    private static Integer toInteger(Object v) {
        return v != null ? ((Number) v).intValue() : null;
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