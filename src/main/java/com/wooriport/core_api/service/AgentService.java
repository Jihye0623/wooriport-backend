package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.agent.*;
import com.wooriport.core_api.base.dto.user.PortiSurveyRequestDto;
import com.wooriport.core_api.base.exception.AiServiceException;
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
    private final InvestorMastersRepository investorMastersRepository;

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
        List<AgentProfileResponseDto.CategoryExpenseItem> sorted = categoryExpense.stream()
                .map(c -> AgentProfileResponseDto.CategoryExpenseItem.builder()
                        .name(c.getName())
                        .amount(c.getAmount())
                        .ratio(totalFinal > 0 ? (int)(c.getAmount() * 100 / totalFinal) : 0)
                        .build())
                .sorted(Comparator.comparingInt(
                        AgentProfileResponseDto.CategoryExpenseItem::getRatio).reversed())
                .collect(Collectors.toList());

        // 정수 truncation으로 잃은 나머지를 가장 큰 항목에 보정
        if (!sorted.isEmpty()) {
            int remainder = 100 - sorted.stream().mapToInt(AgentProfileResponseDto.CategoryExpenseItem::getRatio).sum();
            AgentProfileResponseDto.CategoryExpenseItem first = sorted.get(0);
            sorted.set(0, AgentProfileResponseDto.CategoryExpenseItem.builder()
                    .name(first.getName())
                    .amount(first.getAmount())
                    .ratio(first.getRatio() + remainder)
                    .build());
        }
        categoryExpense = sorted;

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

        // CREDIT_CARD, DEBIT_CARD 제외
        Set<Assets.AccountType> EXCLUDED = Set.of(
                Assets.AccountType.CREDIT_CARD, Assets.AccountType.DEBIT_CARD);

        Set<Assets.AccountType> SAFE_TYPES = Set.of(
                Assets.AccountType.CHECKING, Assets.AccountType.PARKING,
                Assets.AccountType.SAVINGS,  Assets.AccountType.DEPOSIT,
                Assets.AccountType.CMA,      Assets.AccountType.HOUSING_SUBSCRIPTION);

        Set<Assets.AccountType> MODERATE_TYPES = Set.of(
                Assets.AccountType.IRP,
                Assets.AccountType.ISA,
                Assets.AccountType.PENSION_SAVINGS);

        long totalBalance    = assets.stream()
                .filter(a -> !EXCLUDED.contains(a.getAssetType()))
                .mapToLong(Assets::getBalance).sum();

        long safeBalance     = assets.stream()
                .filter(a -> SAFE_TYPES.contains(a.getAssetType()))
                .mapToLong(Assets::getBalance).sum();

        long moderateBalance = assets.stream()
                .filter(a -> MODERATE_TYPES.contains(a.getAssetType()))
                .mapToLong(Assets::getBalance).sum();

        long riskBalance     = assets.stream()
                .filter(a -> a.getAssetType() == Assets.AccountType.STOCK)
                .mapToLong(Assets::getBalance).sum();

        int safeRatio     = totalBalance > 0 ? (int)(safeBalance     * 100 / totalBalance) : 0;
        int moderateRatio = totalBalance > 0 ? (int)(moderateBalance  * 100 / totalBalance) : 0;
        int riskRatio     = 100 - safeRatio - moderateRatio;

        AgentProfileResponseDto.InvestTendency investTendency =
                AgentProfileResponseDto.InvestTendency.builder()
                        .safeRatio(safeRatio)
                        .moderateRatio(moderateRatio)
                        .riskRatio(riskRatio)
                        .build();

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
        flaskBody.put("assets_safe",     safeBalance);
        flaskBody.put("assets_moderate", moderateBalance);
        flaskBody.put("assets_risky",    riskBalance);

        Map<String, Object> flaskResponse = callFlask("/portfolio/profile", flaskBody);

        // ──────────────────────────────────────
        // STEP 7. 거장 조회 (portiType 매칭)
        // ──────────────────────────────────────
        AgentProfileResponseDto.InvestorMasterItem investor =
                investorMastersRepository.findByPortiTypeWithItems(portiResult.getPortiType().name())
                        .map(m -> AgentProfileResponseDto.InvestorMasterItem.builder()
                                .id(m.getId())
                                .name(m.getName())
                                .description(m.getDescription())
                                .hashtag1(m.getHashtag1())
                                .hashtag2(m.getHashtag2())
                                .investmentStyle(m.getInvestmentStyle())
                                .items(m.getItems().stream()
                                        .map(i -> AgentProfileResponseDto.PortfolioItem.builder()
                                                .id(i.getId())
                                                .stockName(i.getStockName())
                                                .changeRate(i.getChangeRate())
                                                .sharesHeld(i.getSharesHeld())
                                                .prevQuarterRatio(i.getPrevQuarterRatio())
                                                .currentRatio(i.getCurrentRatio())
                                                .holdingMonths(i.getHoldingMonths())
                                                .build())
                                        .collect(Collectors.toList()))
                                .build())
                        .orElse(null);

        // ──────────────────────────────────────
        // STEP 8. 응답 조합
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
                .expenseComment(getString(flaskResponse, "expense_comment"))
                .investComment(getString(flaskResponse, "invest_comment"))
                .investor(investor)
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

        // 4. 보유 계좌 조회 — CHECKING/PARKING/DEPOSIT/CMA 중 월급 통장 제외
        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);

        Set<Assets.AccountType> REBALANCE_TYPES = Set.of(
                Assets.AccountType.CHECKING,
                Assets.AccountType.PARKING,
                Assets.AccountType.DEPOSIT,
                Assets.AccountType.CMA
        );
        UUID autoTransferAssetId = user.getAutoTransferToAssetId();

        List<Map<String, Object>> assetList = assets.stream()
                .filter(a -> REBALANCE_TYPES.contains(a.getAssetType()))
                .filter(a -> !a.getId().equals(autoTransferAssetId))
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
                    String assetId        = getString(p, "asset_id");
                    String accountPurpose = getString(p, "account_purpose");
                    Long amount           = p.get("amount") != null
                            ? ((Number) p.get("amount")).longValue()
                            : 0L;
                    String comment        = getString(p, "comment");

                    Assets matched = assetIdMap.get(assetId);

                    return AgentRecommendResponseDto.RebalancingPlan.builder()
                            .assetId(matched != null ? matched.getId() : null)
                            .institution(matched != null ? matched.getInstitution() : null)
                            .assetType(matched != null ? matched.getAssetType().name() : null)
                            .assetNumber(matched != null ? matched.getAssetNumber() : null)
                            .amount(amount)
                            .nickname(accountPurpose)
                            .comment(comment)
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
                .reasoning(getString(flaskResponse, "reasoning"))
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

        // 4. 기존 흐름 중 '미확정(isActive=false)'만 삭제 — 새 처방전 1세트로 교체
        //    '관리 시작하기'로 확정(isActive=true)한 포트폴리오는 보존
        List<PortfolioFlows> existing = portfolioFlowRepository.findAllByUserIdWithDetails(userId).stream()
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
            String title   = getString(flowDto, "title");
            String summary = getString(flowDto, "summary");
            String term    = getString(flowDto, "term");   // 단기/중기/장기 등 원본 그대로
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
                    .title(title != null ? title : "")
                    .summary(summary)
                    .term(term)
                    .amount(flowAmount)
                    .gatheringAsset(gatheringAsset)
                    .gatheringName(gatheringAccount != null ? getString(gatheringAccount, "name") : null)
                    .gatheringType(gatheringAccount != null ? getString(gatheringAccount, "type") : null)
                    .gatheringInstitution(gatheringAccount != null ? getString(gatheringAccount, "institution") : null)
                    .gatheringInterestRate(gatheringAccount != null ? toDouble(gatheringAccount.get("interest_rate")) : null)
                    .reasoning(getString(flowDto, "reasoning"))
                    .expectedRrPct(toDouble(flowDto.get("expected_rr_pct")))
                    .investmentMonths(toInteger(flowDto.get("investment_months")))
                    .expectedAmount(toDouble(flowDto.get("expected_amount")))
                    .rrComment(getString(flowDto, "rr_comment"))
                    .isActive(false)
                    .build();
            PortfolioFlows savedFlow = portfolioFlowRepository.save(flow);

            // portfolio → PUT (상품 + 비율 + 코멘트)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> portfolio =
                    (List<Map<String, Object>>) flowDto.get("portfolio");
            if (portfolio != null) {
                for (Map<String, Object> p : portfolio) {
                    String name = getString(p, "name");
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

        log.info("[AgentService] AI 포트폴리오 생성 완료 — userId={}, flows={}",
                userId, investmentFlows.size());
    }

    // FastAPI 응답(JSON number/string)의 안전한 형 변환 헬퍼
    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

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
            throw new AiServiceException("AI 서버 호출 실패: " + e.getMessage());
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