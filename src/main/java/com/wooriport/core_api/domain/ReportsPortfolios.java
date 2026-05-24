package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "reports_portfolios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReportsPortfolios extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Reports report;

    // nullable — 목표 없이 상품만 있는 경우 가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    // nullable — 상품 정보 없는 경우 가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Products product;

    // SAVING(적금) / DEPOSIT(예금) / STOCK(주식) / BOND(채권) / IRP
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 20)
    private PortfolioItems.ProductType productType;

    // 해당 시점 누적 달성 금액 (스냅샷)
    @Column(name = "current_amount")
    private Long currentAmount;

    // 이번 달 실제 납입 금액
    @Column(name = "real_amount")
    private Long realAmount;
}
