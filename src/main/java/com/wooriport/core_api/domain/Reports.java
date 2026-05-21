package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "reports")
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

    @Column(name = "total_income", nullable = false)
    @Builder.Default
    private Long totalIncome = 0L;

    @Column(name = "total_expense", nullable = false)
    @Builder.Default
    private Long totalExpense = 0L;

    // 잉여자금 = total_income - total_expense
    @Column(name = "surplus", nullable = false)
    @Builder.Default
    private Long surplus = 0L;

    // monthly_change: "이번 달 주식 비중이 5% 증가했어요..."
    @Column(name = "monthly_change", columnDefinition = "TEXT")
    private String monthlyChange;

    // portfolios: {"stock_change": 5.2, "bond_change": -1.3, "cash_change": -3.9}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "portfolios", columnDefinition = "jsonb")
    private String portfolios;

    // portfolio_comment: "전반적으로 안정적인 포트폴리오를 유지하고 있어요..."
    @Column(name = "portfolio_comment", columnDefinition = "TEXT")
    private String portfolioComment;

    // ── 소비 패턴 ──────────────────────────────
    // expense_categories: [{"category": "식비", "value": 287000}, ...]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expense_categories", columnDefinition = "jsonb")
    private String expenseCategories;

    // expense_analysis: "식비 지출이 가장 높았어요.\n여가비가 전월 대비 15% 증가했어요."
    @Column(name = "expense_analysis", columnDefinition = "TEXT")
    private String expenseAnalysis;

    // ── 다음달 가이드라인 ──────────────────────
    // recommended_rebalance_ratio: {"stock_ratio": 50, "bond_ratio": 30, "cash_ratio": 20}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_rebalance_ratio", columnDefinition = "jsonb")
    private String recommendedRebalanceRatio;

    // next_month_guideline: "다음 달엔 식비를 10% 줄이고..."
    @Column(name = "next_month_guideline", columnDefinition = "TEXT")
    private String nextMonthGuideline;

    public void calculateSurplus() {
        this.surplus = this.totalIncome - this.totalExpense;
    }
}