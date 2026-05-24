package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "portfolio_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PortfolioItems extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Products product;

    // SAVING(적금) / DEPOSIT(예금) / STOCK(주식) / BOND(채권)
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    // 배분 비율 0~100 (합산 100%)
    @Column(name = "product_ratio", nullable = false)
    private Integer productRatio;

    // 계좌 연동 (신규)
    // NULL = 아직 계좌 미연동 상태
    // 값 있음 = 계좌 연동 완료 → 리밸런싱 가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Assets asset;

    public void linkAsset(Assets asset) {
        this.asset = asset;
    }

    public void unlinkAsset() {
        this.asset = null;
    }

    // 계좌 연동 여부 확인
    public boolean isLinked() {
        return this.asset != null;
    }

    // 비즈니스 메서드
    public void updateRatio(Integer ratio) {
        this.productRatio = ratio;
    }

    public enum ProductType {
        SAVING, DEPOSIT, STOCK, BOND
    }
}
