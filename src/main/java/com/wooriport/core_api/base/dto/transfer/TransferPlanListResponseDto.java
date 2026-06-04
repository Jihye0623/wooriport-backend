package com.wooriport.core_api.base.dto.transfer;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

// ─────────────────────────────────────────
// GET·POST /transfer-plans 응답
// ─────────────────────────────────────────
@Getter
@Builder
public class TransferPlanListResponseDto {
    private List<PlanItem> plans;
    private Long totalAmount;
    private Long salaryAmount;
    private String rebalanceComment;

    @Getter
    @Builder
    public static class PlanItem {
        private UUID id;
        private UUID assetId;
        private String institution;   // 금융사명
        private String assetType;       // SPENDING / EMERGENCY / TARGET / SAVING
        private Long plannedAmount;
        private Boolean isConfirmed;
        private int year;
        private int month;
    }
}
