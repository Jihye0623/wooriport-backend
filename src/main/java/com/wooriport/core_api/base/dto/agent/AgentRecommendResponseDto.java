package com.wooriport.core_api.base.dto.agent;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AgentRecommendResponseDto {

    private Long salary;                  // 이번 달 급여
    private Long investAmount;            // 추천 투자 금액
    private Long totalFixedExpense;       // 고정 지출 합계
    private String fixedExpenseComment;   // 고정 지출 안내 문구

    private List<RebalancingPlan> rebalancingPlans;
    private Long remainingAmount;         // 남은 금액

    @Getter
    @Builder
    public static class RebalancingPlan {
        private UUID assetId;
        private String institution;
        private String assetType;
        private String assetNumber;
        private Long amount;
        private String nickname;
        private String comment;
    }
}
