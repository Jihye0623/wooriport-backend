package com.wooriport.core_api.base.dto.agent;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AgentInputResponseDto {

    private EventSummary event;             // AI가 파싱한 이벤트 정보
    private Long investAmount;             // 새 추천 투자 금액
    private Long investAmountDiff;         // 투자 금액 변동 (음수 = 감소)
    private List<RebalancingPlan> rebalancingPlans;
    private Long remainingAmount;

    @Getter @Builder
    public static class EventSummary {
        private String title;
        private Long targetAmount;
        private Long monthlySaving;
        private String deadline;
    }

    @Getter @Builder
    public static class RebalancingPlan {
        private UUID assetId;
        private String institution;
        private String assetType;
        private String assetNumber;
        private Long amount;
        private String nickname;
        private Long previousAmount;   // 기존 금액
        private Long diff;             // 변동 금액
    }
}