package com.wooriport.core_api.base.dto.report;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ReportDetailResponseDto {

    private UUID id;
    private int year;
    private int month;

    // 수지 요약
    private Long totalIncome;
    private Long totalExpense;
    private Long surplus;

    // AI 텍스트
    private String trendComment;          // 자산 변화 코멘트
    private String eventComment;          // 목표 도달율 코멘트
    private String marketCondition;       // 시장 상황 요약
    private String guideline;             // 다음달 소비 가이드라인

    // 전달 대비 자산 변화율 (%, null이면 비교 데이터 없음)
    private Double assetChangeRate;

    // 주별 총자산 스냅샷 (그래프용)
    private List<AssetSnapshot> assetSnapshots;

    // 주별 누적 소비 (이번달 vs 전달 꺾은선 그래프용)
    private List<WeeklyExpenseSnapshot> weeklyExpenses;

    // 카테고리별 소비
    private List<CategoryExpenseItem> categoryExpenses;

    // 포트폴리오 자산 유형별 비중
    private List<PortfolioBreakdownItem> portfolioBreakdown;

    // 세제혜택 납입 요약
    private TaxBenefitSummary taxBenefitSummary;

    // 해당 월 미니챌린지
    private List<MiniChallengeItem> miniChallenges;

    private String createdAt;

    @Getter
    @Builder
    public static class AssetSnapshot {
        private String snapshotDate;
        private Long totalAmount;
    }

    @Getter
    @Builder
    public static class WeeklyExpenseSnapshot {
        private int week;
        private Long currCumulative;
        private Long prevCumulative;
    }

    @Getter
    @Builder
    public static class CategoryExpenseItem {
        private String category;
        private Long amount;
        private Long prevAmount;
        private Integer ratio;
        private String hoverComment;
    }

    @Getter
    @Builder
    public static class TaxBenefitSummary {
        private Long irpContribution;              // IRP 이번달 납입액
        private Long irpCumulativeDeduction;       // IRP 누적 공제액 (잔액 기준)
        private Long pensionContribution;          // 연금저축 이번달 납입액
        private Long pensionCumulativeDeduction;   // 연금저축 누적 공제액 (잔액 기준)
        private Long totalTaxSavings;              // IRP + 연금저축 합산 절세액
    }

    @Getter
    @Builder
    public static class PortfolioBreakdownItem {
        private String productName;
        private String productType;       // "ETF" or "BOND_FUND"
        private String ticker;
        private Double monthlyChangeRate; // 전달 대비 등락률(%), null이면 조회 실패
    }

    @Getter
    @Builder
    public static class MiniChallengeItem {
        private String title;
        private String challengeSubType;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String status;
        private String rewardStockTicker;
    }
}
