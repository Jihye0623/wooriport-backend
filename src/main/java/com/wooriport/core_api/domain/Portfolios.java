package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "portfolios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Portfolios extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // 자산 유형
    // STOCK    주식
    // BOND     채권
    // CASH     현금/예금
    // IRP      개인형 퇴직연금
    // EMERGENCY 비상금
    // FIXED    고정비 (생활비 등)
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    // 월급 대비 비율 (%)
    @Column(name = "asset_ratio", nullable = false)
    private Integer assetRatio;

    // 연동 계좌 (nullable — 미연동 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Assets asset;

    // 비즈니스 메서드
    public void updateRatio(Integer ratio) {
        this.assetRatio = ratio;
    }

    public void linkAsset(Assets asset) {
        this.asset = asset;
    }

    public void unlinkAsset() {
        this.asset = null;
    }

    public boolean isLinked() {
        return this.asset != null;
    }

    public enum AssetType {
        STOCK,      // 주식
        BOND,       // 채권
        CASH,       // 현금/예금
        IRP,        // 개인형 퇴직연금
        EMERGENCY,  // 비상금
        FIXED       // 고정비
    }
}