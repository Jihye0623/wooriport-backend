package com.wooriport.core_api.domain;
import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reports_user_year_month",
                columnNames = {"user_id", "year", "month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Reports extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    // 수지 요약
    @Column(name = "total_income", nullable = false)
    @Builder.Default
    private Long totalIncome = 0L;

    @Column(name = "total_expense", nullable = false)
    @Builder.Default
    private Long totalExpense = 0L;

    @Column(name = "surplus", nullable = false)
    @Builder.Default
    private Long surplus = 0L;

    // 전달 대비 자산 변화
    @Column(name = "prev_total_amount")
    private Long prevTotalAmount;

    @Column(name = "curr_total_amount")
    private Long currTotalAmount;

    @Column(name = "prev_savings_amount")
    private Long prevSavingsAmount;

    @Column(name = "curr_savings_amount")
    private Long currSavingsAmount;

    @Column(name = "prev_invest_amount")
    private Long prevInvestAmount;

    @Column(name = "curr_invest_amount")
    private Long currInvestAmount;

    // AI 생성 텍스트
    @Column(name = "portfolio_comment", columnDefinition = "TEXT")
    private String portfolioComment;        // trend_comment

    @Column(name = "event_comment", columnDefinition = "TEXT")
    private String eventComment;

    @Column(name = "market_summary", columnDefinition = "TEXT")
    private String marketSummary;           // market_condition

    @Column(name = "next_month_guideline", columnDefinition = "TEXT")
    private String nextMonthGuideline;      // guideline

    @Column(name = "performance_status", length = 20)
    private String performanceStatus;

    @Column(name = "performance_comment", columnDefinition = "TEXT")
    private String performanceComment;

    // 주별 총자산 스냅샷 JSON (프론트 그래프용)
    // [{"snapshotDate":"2026-03-07","totalAmount":15000000}, ...]
    @Column(name = "asset_snapshots_json", columnDefinition = "TEXT")
    private String assetSnapshotsJson;

    // 주별 누적 소비 JSON (이번달 vs 전달 비교 꺾은선 그래프용)
    // [{"week":1,"currCumulative":150000,"prevCumulative":120000}, ...]
    @Column(name = "weekly_expenses_json", columnDefinition = "TEXT")
    private String weeklyExpensesJson;

    // 카테고리별 소비 (양방향)
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReportCategoryExpenses> categoryExpenses = new ArrayList<>();

    // 자산 변화 저장
    public void updateAssetChanges(Long prevTotal,   Long currTotal,
                                   Long prevSavings, Long currSavings,
                                   Long prevInvest,  Long currInvest) {
        this.prevTotalAmount   = prevTotal;
        this.currTotalAmount   = currTotal;
        this.prevSavingsAmount = prevSavings;
        this.currSavingsAmount = currSavings;
        this.prevInvestAmount  = prevInvest;
        this.currInvestAmount  = currInvest;
    }

    // 총자산 변화율 (%)
    public Double getTotalChangeRate() {
        if (prevTotalAmount == null || prevTotalAmount == 0) return null;
        return (double)(currTotalAmount - prevTotalAmount) / prevTotalAmount * 100;
    }

    // 투자 변화율 (%)
    public Double getInvestChangeRate() {
        if (prevInvestAmount == null || prevInvestAmount == 0) return null;
        return (double)(currInvestAmount - prevInvestAmount) / prevInvestAmount * 100;
    }
}