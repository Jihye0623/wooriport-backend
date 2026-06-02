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

    // PULL 총 금액 (끌어올 금액 합계 — PUT 비율 계산 기준)
    @Column(name = "amount")
    private Long amount;

    // step2 — 모을 통장 (단일). 보유 계좌 선택이면 연결, 계좌 추천이면 null (추천 정보는 아래 gathering_* 에 저장)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_asset_id")
    private Assets gatheringAsset;

    // 계좌 추천일 때(gatheringAsset=null)의 추천 통장 정보 — AI 응답의 gathering_account
    @Column(name = "gathering_name", length = 100)
    private String gatheringName;

    @Column(name = "gathering_type", length = 30)
    private String gatheringType;

    @Column(name = "gathering_institution", length = 100)
    private String gatheringInstitution;

    @Column(name = "gathering_interest_rate")
    private Double gatheringInterestRate;

    // 모을 통장 추천 이유 (AI)
    @Column(name = "account_comment", columnDefinition = "TEXT")
    private String accountComment;

    // 1년 예상 수익률 (%)
    @Column(name = "expected_rr_pct")
    private Double expectedRrPct;

    // 예상 산정 기간 (N개월)
    @Column(name = "investment_months")
    private Integer investmentMonths;

    // 복리 고려 N개월 후 예상 수익
    @Column(name = "expected_amount")
    private Double expectedAmount;

    // 수익률 코멘트 (AI)
    @Column(name = "rr_comment", columnDefinition = "TEXT")
    private String rrComment;

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

    public void updateAmount(Long amount) {
        this.amount = amount;
    }

    // 모을 통장 변경
    public void updateGatheringAsset(Assets asset) {
        this.gatheringAsset = asset;
    }

    // 보유 계좌 연결(또는 추천 계좌 개설 후 연결) — 추천 표시 정보는 정리
    public void linkGatheringAsset(Assets asset) {
        this.gatheringAsset = asset;
        this.gatheringName = null;
        this.gatheringType = null;
        this.gatheringInstitution = null;
        this.gatheringInterestRate = null;
    }
}

