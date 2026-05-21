package com.wooriport.core_api.base.dto.report;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

// ─────────────────────────────────────────
// GET /reports/{year}/{month} 응답
// ─────────────────────────────────────────
@Getter
@Builder
public class ReportDetailResponseDto {

    private UUID id;
    private int year;
    private int month;

    // 수치
    private Long totalIncome;
    private Long totalExpense;
    private Long surplus;

    // 포트폴리오 변화
    private String monthlyChange;           // 변화 코멘트
    private PortfolioChange portfolios;     // 주식/채권/현금 +- %
    private String portfolioComment;        // 자산군 전체 상황 요약

    // 소비 패턴 (예산 vs 실제 비교까지 포함)
    private List<SpendingItem> expenseCategories;  // 프론트 차트용
    private String expenseAnalysis;                // 2줄 설명

    // 다음달 가이드라인
    private RebalanceRatio recommendedRebalanceRatio;
    private String nextMonthGuideline;

    private String createdAt;

    // ── 내부 DTO ────────────────────────────

    @Getter
    @Builder
    public static class PortfolioChange {
        private Float stockChange;   // +5.2
        private Float bondChange;    // -1.3
        private Float cashChange;    // -3.9
    }

    @Getter
    @Builder
    public static class SpendingItem {
        private String category;  // 식비
        private Long actual;      // 실제 지출 287,000
        private Long budget;      // 예산 300,000 (spending_budgets에서 조회)
        private Integer ratio;    // 예산 대비 실제 비율 (%) — 초과 시 100 이상
    }

    @Getter
    @Builder
    public static class RebalanceRatio {
        private Integer stockRatio;  // 50
        private Integer bondRatio;   // 30
        private Integer cashRatio;   // 20
    }
}