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

    // 단계 구분: PULL(끌어오기) / PUT(넣기)
    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 10)
    private StepType stepType;

    // 연동 계좌 (PULL — 끌어올 계좌 / PUT — 가입할 계좌, null 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Assets asset;

    // PULL일 때 — 끌어올 금액
    @Column(name = "amount")
    private Long amount;

    // 상품 연동 (PUT일 때 — 어떤 상품에 투자할지, null 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Products product;

    // PUT일 때 — 상품 유형 (STOCK / BOND / DEPOSIT)
    @Column(name = "product_type", length = 20)
    private String productType;

    // PUT일 때 — 투자 비율 (%)
    @Column(name = "product_ratio")
    private Integer productRatio;

    // ──────────────────────────────────────
    // 비즈니스 메서드
    // ──────────────────────────────────────

    // PULL 여부 확인
    public boolean isPull() {
        return this.stepType == StepType.PULL;
    }

    // PUT 여부 확인
    public boolean isPut() {
        return this.stepType == StepType.PUT;
    }

    // 금액 수정 (PULL)
    public void updateAmount(Long amount) {
        this.amount = amount;
    }

    // 비율 수정 (PUT)
    public void updateRatio(Integer ratio) {
        this.productRatio = ratio;
    }

    // ──────────────────────────────────────
    // Enum
    // ──────────────────────────────────────
    public enum StepType {
        PULL,   // 끌어오기 (step1)
        PUT     // 넣기 (step3)
        // step2(모을 통장)는 PortfolioFlows.gatheringAsset에 저장
    }
}