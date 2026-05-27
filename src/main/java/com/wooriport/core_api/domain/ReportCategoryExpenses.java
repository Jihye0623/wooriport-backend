package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "report_category_expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReportCategoryExpenses extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Reports report;

    // 카테고리명 (식비 / 쇼핑 / 교통 / 여가 등)
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    // 이번달 금액
    @Column(name = "amount", nullable = false)
    private Long amount;

    // 전달 금액
    @Column(name = "prev_amount")
    private Long prevAmount;

    // 전체 지출 대비 비율 (%)
    @Column(name = "ratio", nullable = false)
    private Integer ratio;

    // 호버 설명 (Flask 생성, 2줄)
    @Column(name = "hover_comment", columnDefinition = "TEXT")
    private String hoverComment;

    // Flask 호버 코멘트 업데이트
    public void updateHoverComment(String comment) {
        this.hoverComment = comment;
    }

    // 전달 대비 변화 금액
    public Long getChangeAmount() {
        if (prevAmount == null) return null;
        return this.amount - this.prevAmount;
    }

    // 전달 대비 변화율 (%)
    public Double getChangeRate() {
        if (prevAmount == null || prevAmount == 0) return null;
        return (double)(this.amount - this.prevAmount) / this.prevAmount * 100;
    }

    // 증가 여부
    public boolean isIncreased() {
        if (prevAmount == null) return false;
        return this.amount > this.prevAmount;
    }
}