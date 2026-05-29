package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.AssetCategory;
import com.wooriport.core_api.domain.common.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "transfer_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TransferPlans extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // 이체 목적지 통장
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Assets asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    private AssetCategory assetType;

    // AI가 제안한 이번 달 이체 금액
    @Column(name = "planned_amount", nullable = false)
    private Long plannedAmount;

    // 사용자 확인 버튼 여부 (false = 대기 중)
    @Column(name = "is_confirmed", nullable = false)
    @Builder.Default
    private Boolean isConfirmed = false;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    // 비즈니스 메서드
    public void confirm() {
        this.isConfirmed = true;
    }

    public void resetConfirm() {
        this.isConfirmed = false;
    }

    public void updatePlannedAmount(Long amount) {
        this.plannedAmount = amount;
        this.isConfirmed = false; // 금액 변경 시 재확인 필요
    }
}
