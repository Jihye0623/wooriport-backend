package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolio_flows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PortfolioFlows extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // null = 기본 포트폴리오 / 있으면 이벤트 포트폴리오
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    // 흐름 이름 (예: "흐름 A")
    @Column(name = "title", nullable = false, length = 50)
    private String title;

    // 한 줄 요약 (예: "비상금·생활비 베이스를 단단히 다져요")
    @Column(name = "summary", length = 200)
    private String summary;

    // 흐름 성격: '단' / '중' / '장'
    @Column(name = "term", length = 50)
    private String term;

    // step2 — 모을 통장 (단일)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_asset_id")
    private Assets gatheringAsset;

    // 활성화 여부
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    // 활성화 시작 시각
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    // 흐름 항목 (양방향 매핑)
    @OneToMany(mappedBy = "flow", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PortfolioFlowItems> items = new ArrayList<>();

    // ──────────────────────────────────────
    // 비즈니스 메서드
    // ──────────────────────────────────────

    // 흐름 활성화
    public void activate() {
        this.isActive = true;
        this.startedAt = LocalDateTime.now();
    }

    // 흐름 비활성화
    public void deactivate() {
        this.isActive = false;
    }

    // 제목 수정
    public void updateTitle(String title) {
        this.title = title;
    }

    // 한 줄 요약 수정
    public void updateSummary(String summary) {
        this.summary = summary;
    }

    // 기간 수정 (단/중/장)
    public void updateTerm(String term) {
        this.term = term;
    }

    // 모을 통장 변경
    public void updateGatheringAsset(Assets asset) {
        this.gatheringAsset = asset;
    }
}

