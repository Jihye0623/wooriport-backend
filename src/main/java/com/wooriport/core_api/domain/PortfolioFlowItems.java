package com.wooriport.core_api.domain;


import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "portfolio_flow_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PortfolioFlowItems extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    // 소속 흐름
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    private PortfolioFlows flow;

    // 연동 계좌 (PULL — 끌어올 계좌 / PUT — 가입할 계좌, null 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Assets asset;

    // 상품 연동 (PUT일 때 — 어떤 상품에 투자할지, null 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Products product;

    // PUT일 때 — 투자 비율 (%)
    @Column(name = "product_ratio")
    private Integer productRatio;

    // AI 코멘트
    @Column(name = "ai_comment", columnDefinition = "TEXT")
    private String aiComment;

    // ──────────────────────────────────────
    // 비즈니스 메서드
    // ──────────────────────────────────────

    // PULL(끌어오기) 여부 — 투자 비율이 없는 항목
    public boolean isPull() {
        return this.productRatio == null;
    }

    // PUT(넣기) 여부 — 투자 비율이 있는 항목
    public boolean isPut() {
        return this.productRatio != null;
    }

    // 비율 수정 (PUT)
    public void updateRatio(Integer ratio) {
        this.productRatio = ratio;
    }
}