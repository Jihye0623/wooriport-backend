package com.wooriport.core_api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.report.ReportDetailResponseDto;
import com.wooriport.core_api.base.dto.report.ReportListResponseDto;
import com.wooriport.core_api.domain.Reports;
import com.wooriport.core_api.domain.SpendingBudgets;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.ReportRepository;
import com.wooriport.core_api.repository.SpendingBudgetRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final SpendingBudgetRepository spendingBudgetRepository;
    private final UserRepository userRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

    // ──────────────────────────────────────
    // GET /reports — 목록
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public ReportListResponseDto getReports(UUID userId) {
        List<Reports> reports = reportRepository
                .findByUserIdOrderByYearDescMonthDesc(userId);

        List<ReportListResponseDto.ReportItem> items = reports.stream()
                .map(r -> ReportListResponseDto.ReportItem.builder()
                        .id(r.getId())
                        .year(r.getYear())
                        .month(r.getMonth())
                        .totalIncome(r.getTotalIncome())
                        .totalExpense(r.getTotalExpense())
                        .surplus(r.getSurplus())
                        .createdAt(r.getCreatedAt().toString())
                        .build())
                .collect(Collectors.toList());

        return ReportListResponseDto.builder()
                .reports(items)
                .totalCount(items.size())
                .build();
    }

    // ──────────────────────────────────────
    // GET /reports/{year}/{month} — 상세
    // spending_budgets 조회 후 expense_categories에 예산 합산
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public ReportDetailResponseDto getReport(UUID userId, int year, int month) {
        Reports report = reportRepository
                .findByUserIdAndYearAndMonth(userId, year, month)
                .orElseThrow(() -> new IllegalArgumentException(
                        year + "년 " + month + "월 리포트가 없습니다."));

        // expense_categories JSON → Map (카테고리별 실제 지출)
        // [{"category": "식비", "value": 287000}, ...] → {"식비": 287000}
        Map<String, Long> actualMap = parseExpenseCategories(report.getExpenseCategories());

        // spending_budgets 조회 → 카테고리별 예산
        List<SpendingBudgets> budgets = spendingBudgetRepository
                .findByUserIdAndYearAndMonth(userId, year, month);
        Map<String, Long> budgetMap = budgets.stream()
                .collect(Collectors.toMap(
                        SpendingBudgets::getCategory,
                        SpendingBudgets::getAmount,
                        (a, b) -> a));

        // 실제 지출 기준으로 SpendingItem 생성 (예산 없는 카테고리도 포함)
        List<ReportDetailResponseDto.SpendingItem> spendingItems = actualMap.entrySet().stream()
                .map(e -> {
                    String category = e.getKey();
                    Long actual     = e.getValue();
                    Long budget     = budgetMap.getOrDefault(category, 0L);
                    int ratio       = budget > 0 ? (int)(actual * 100 / budget) : 0;

                    return ReportDetailResponseDto.SpendingItem.builder()
                            .category(category)
                            .actual(actual)
                            .budget(budget)
                            .ratio(ratio)
                            .build();
                })
                .collect(Collectors.toList());

        // portfolios JSON 파싱
        ReportDetailResponseDto.PortfolioChange portfolioChange =
                parsePortfolios(report.getPortfolios());

        // recommendedRebalanceRatio JSON 파싱
        ReportDetailResponseDto.RebalanceRatio rebalanceRatio =
                parseRebalanceRatio(report.getRecommendedRebalanceRatio());

        return ReportDetailResponseDto.builder()
                .id(report.getId())
                .year(report.getYear())
                .month(report.getMonth())
                .totalIncome(report.getTotalIncome())
                .totalExpense(report.getTotalExpense())
                .surplus(report.getSurplus())
                .monthlyChange(report.getMonthlyChange())
                .portfolios(portfolioChange)
                .portfolioComment(report.getPortfolioComment())
                .expenseCategories(spendingItems)
                .expenseAnalysis(report.getExpenseAnalysis())
                .recommendedRebalanceRatio(rebalanceRatio)
                .nextMonthGuideline(report.getNextMonthGuideline())
                .createdAt(report.getCreatedAt().toString())
                .build();
    }

    // ──────────────────────────────────────
    // Spring Batch — 리포트 생성
    // ──────────────────────────────────────
    @Transactional
    public void generateMonthlyReport(UUID userId, int year, int month) {

        if (reportRepository.existsByUserIdAndYearAndMonth(userId, year, month)) {
            log.info("[ReportJob] 이미 생성됨 — userId: {}, {}년 {}월", userId, year, month);
            return;
        }

        // Flask 한 번 호출 → 모든 데이터 수신
        Map<String, Object> res = requestReport(userId, year, month);
        if (res == null) {
            log.warn("[ReportJob] Flask 응답 없음 — userId: {}", userId);
            return;
        }

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        reportRepository.save(Reports.builder()
                .user(user)
                .year(year)
                .month(month)
                .totalIncome(toLong(res.get("total_income")))
                .totalExpense(toLong(res.get("total_expense")))
                .surplus(toLong(res.get("surplus")))
                .monthlyChange((String) res.get("monthly_change"))
                .portfolios(toJson(res.get("portfolios")))
                .portfolioComment((String) res.get("portfolio_comment"))
                .expenseCategories(toJson(res.get("expense_categories")))
                .expenseAnalysis((String) res.get("expense_analysis"))
                .recommendedRebalanceRatio(toJson(res.get("recommended_rebalance_ratio")))
                .nextMonthGuideline((String) res.get("next_month_guideline"))
                .build());

        log.info("[ReportJob] 완료 — userId: {}, {}년 {}월", userId, year, month);
    }

    // ──────────────────────────────────────
    // Flask POST 요청
    // ──────────────────────────────────────
    private Map<String, Object> requestReport(UUID userId, int year, int month) {
        try {
            return webClient.post()
                    .uri(flaskMlUrl + "/agent/report")
                    .bodyValue(Map.of(
                            "user_id", userId.toString(),
                            "year",    year,
                            "month",   month))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("[ReportJob] Flask 요청 실패: {}", e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────
    // JSON 파싱 헬퍼
    // ──────────────────────────────────────

    // [{"category": "식비", "value": 287000}, ...] → {"식비": 287000}
    private Map<String, Long> parseExpenseCategories(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<>() {});
            return list.stream().collect(Collectors.toMap(
                    m -> (String) m.get("category"),
                    m -> toLong(m.get("value")),
                    (a, b) -> a));
        } catch (Exception e) {
            log.warn("expense_categories 파싱 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    // {"stock_change": 5.2, "bond_change": -1.3, "cash_change": -3.9}
    private ReportDetailResponseDto.PortfolioChange parsePortfolios(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return ReportDetailResponseDto.PortfolioChange.builder()
                    .stockChange(toFloat(map.get("stock_change")))
                    .bondChange(toFloat(map.get("bond_change")))
                    .cashChange(toFloat(map.get("cash_change")))
                    .build();
        } catch (Exception e) {
            log.warn("portfolios 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    // {"stock_ratio": 50, "bond_ratio": 30, "cash_ratio": 20}
    private ReportDetailResponseDto.RebalanceRatio parseRebalanceRatio(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return ReportDetailResponseDto.RebalanceRatio.builder()
                    .stockRatio(toInt(map.get("stock_ratio")))
                    .bondRatio(toInt(map.get("bond_ratio")))
                    .cashRatio(toInt(map.get("cash_ratio")))
                    .build();
        } catch (Exception e) {
            log.warn("recommendedRebalanceRatio 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private Long toLong(Object v) {
        if (v == null) return 0L;
        return Long.valueOf(v.toString());
    }

    private Float toFloat(Object v) {
        if (v == null) return 0f;
        return Float.valueOf(v.toString());
    }

    private Integer toInt(Object v) {
        if (v == null) return 0;
        return Integer.valueOf(v.toString());
    }
}