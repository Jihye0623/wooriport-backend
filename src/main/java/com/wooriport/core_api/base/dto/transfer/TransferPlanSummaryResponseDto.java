package com.wooriport.core_api.base.dto.transfer;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class TransferPlanSummaryResponseDto {

    // 1. 이번달 실제 월급
    private Long currentSalary;
    private Long salaryDiff;           // currentSalary - user.salary

    // 2. portfolios 분배 (3-1)
    private Long portfolioTotal;       // 실제 이체 계획 합계 (AI 조정 포함)
    private Long portfolioTotalDiff;   // vs portfolios.asset_amount 합계
    private List<PortfolioPlanItem> portfolioItems;

    // 3. portfolio_flow 분배 (3-2)
    private Long flowTotal;            // 실제 이체 계획 합계 (AI 조정 포함)
    private Long flowTotalDiff;        // vs flow.amount * productRatio/100 합계
    private List<FlowPlanItem> flowItems;

    // 4. 잔여
    private Long remaining;            // currentSalary - portfolioTotal - flowTotal
    private Long remainingDiff;        // vs (user.salary - portfolioBaseline - flowBaseline)

    // 5. AI 리밸런싱 코멘트 (급여 변동 시에만 존재)
    private String rebalanceComment;

    @Getter
    @Builder
    public static class PortfolioPlanItem {
        private UUID planId;
        private UUID assetId;
        private String institution;
        private String accountName;     // 통장 이름 (Assets.accountName) — 화면 좌측 표시
        private String accountPurpose;  // 통장 별명/용도 (Assets.accountPurpose) "생활비/비상금/적금" — 태그 표시
        private String assetType;
        private Long plannedAmount;    // 실제 (AI 조정 포함)
        private Long baselineAmount;   // portfolios.asset_amount
        private Long diff;             // plannedAmount - baselineAmount
        private Boolean isConfirmed;
    }

    @Getter
    @Builder
    public static class FlowPlanItem {
        private UUID planId;
        private UUID assetId;
        private String institution;
        private String productType;
        private String term;           // 흐름 기간: '단' / '중' / '장'
        private Long plannedAmount;    // 실제 (AI 조정 포함)
        private Long baselineAmount;   // flow.amount * productRatio / 100
        private Long diff;             // plannedAmount - baselineAmount
        private Boolean isConfirmed;
    }
}
