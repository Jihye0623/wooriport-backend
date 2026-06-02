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

    @Column(name = "target_count")
    private Integer targetCount;

    @Column(name = "target_amount")
    private Long targetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ChallengeStatus status = ChallengeStatus.PENDING;

    @Column(name = "reward_stock_ticker", length = 20)
    private String rewardStockTicker;

    @Column(name = "current_amount")
    @Builder.Default
    private Long currentAmount = 0L;

    @Column(name = "current_count")
    @Builder.Default
    private Integer currentCount = 0;

    @Column(name = "notified_threshold")
    @Builder.Default
    private Integer notifiedThreshold = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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

    public void addProgress(long amount, int count) {
        this.currentAmount += amount;
        this.currentCount += count;
    }

    public void syncProgress(long currentAmount, int currentCount, int notifiedThreshold) {
        this.currentAmount = currentAmount;
        this.currentCount = currentCount;
        this.notifiedThreshold = notifiedThreshold;
    }
}
