package com.wooriport.core_api.base.dto.report;

import lombok.Builder;
import lombok.Getter;

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
    private String performanceStatus;     // UNDERPERFORM / OUTPERFORM
    private String performanceComment;

    // 주별 총자산 스냅샷 (그래프용)
    private List<AssetSnapshot> assetSnapshots;

    // 주별 누적 소비 (이번달 vs 전달 꺾은선 그래프용)
    private List<WeeklyExpenseSnapshot> weeklyExpenses;

    // 카테고리별 소비
    private List<CategoryExpenseItem> categoryExpenses;

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
}
