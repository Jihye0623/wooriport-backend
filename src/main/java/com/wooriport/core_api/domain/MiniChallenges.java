package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mini_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MiniChallenges extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", length = 20)
    private ChallengeType challengeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_sub_type", length = 20)
    private ChallengeSubType challengeSubType;

    @Column(name = "target")
    private Long target;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ChallengeStatus status = ChallengeStatus.PENDING;

    @Column(name = "reward_stock_ticker", length = 20)
    private String rewardStockTicker;

    @Column(name = "estimated_saving")
    private Long estimatedSaving;

    @Column(name = "current_value")
    @Builder.Default
    private Long currentValue = 0L;

    @Column(name = "notified_threshold")
    @Builder.Default
    private Integer notifiedThreshold = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum ChallengeType {
        AMOUNT,
        COUNT
    }

    public enum ChallengeSubType {
        COFFEE,      // 카페
        DELIVERY,    // 배달 (식비 + 배달앱)
        ALCOHOL,     // 술 (식비 + 주점)
        LATE_NIGHT,  // 야식 (식비 + 23:00~04:00)
        LUNCH,       // 점심 (식비 + 11:00~14:00)
        SHOPPING,    // 쇼핑 (쇼핑 + 특정 앱)
        TAXI         // 택시 (교통 + 택시)
    }

    public enum ChallengeStatus {
        PENDING,        // 제안됨 (사용자 응답 대기)
        IN_PROGRESS,    // 승인 후 진행 중
        COMPLETED,      // 성공
        FAILED,         // 실패
        REJECTED        // 거절됨
    }

    public void start() {
        this.status = ChallengeStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = ChallengeStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = ChallengeStatus.FAILED;
    }

    public void reject() {
        this.status = ChallengeStatus.REJECTED;
    }

    public void syncProgress(long current, int notifiedThreshold) {
        this.currentValue = current;
        this.notifiedThreshold = notifiedThreshold;
    }
}
