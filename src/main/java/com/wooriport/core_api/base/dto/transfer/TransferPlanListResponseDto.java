package com.wooriport.core_api.base.dto.transfer;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
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
    private Long salaryAmount;  // 총 이체 금액

    @Getter
    @Builder
    public static class PlanItem {
        private UUID id;
        private UUID assetId;
        private String institution;   // 금융사명
        private String assetType;       // SPENDING / EMERGENCY / TARGET / SAVING
        private Long plannedAmount;
        private Double ratio;          // 전체 대비 비율(%)
        private Boolean isConfirmed;
        private int scheduledDate;
        private int year;
        private int month;
    }
}
