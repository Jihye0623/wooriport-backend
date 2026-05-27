package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "asset_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AssetSnapshots extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // 스냅샷 시각 (매주 월요일 자정)
    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    // 총자산
    @Column(name = "total_amount", nullable = false)
    @Builder.Default
    private Long totalAmount = 0L;

    // 현금성 + 적금 (CHECKING, PARKING, SAVINGS, DEPOSIT, CMA)
    @Column(name = "savings_amount", nullable = false)
    @Builder.Default
    private Long savingsAmount = 0L;

    // 주식 + IRP
    @Column(name = "invest_amount", nullable = false)
    @Builder.Default
    private Long investAmount = 0L;
}