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
    private String portfolioComment;

    @Column(name = "market_summary", columnDefinition = "TEXT")
    private String marketSummary;

    @Column(name = "next_month_guideline", columnDefinition = "TEXT")
    private String nextMonthGuideline;

    // 카테고리별 소비 (양방향)
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReportCategoryExpenses> categoryExpenses = new ArrayList<>();

    // AI 텍스트 업데이트 (Flask 응답 수신 시)
    public void updateAiComments(String portfolioComment,
                                 String marketSummary,
                                 String nextMonthGuideline) {
        this.portfolioComment   = portfolioComment;
        this.marketSummary      = marketSummary;
        this.nextMonthGuideline = nextMonthGuideline;
    }

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