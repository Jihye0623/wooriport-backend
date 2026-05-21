package com.wooriport.core_api.base.dto.transfer;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

// ─────────────────────────────────────────
// POST /transfer-plans 요청 바디
// ─────────────────────────────────────────
@Getter
@Builder
public class TransferPlanCreateRequestDto {
    private List<PlanItem> plans;
    private int year;
    private int month;
    private int scheduledDate;  // 매월 이체일 (1~31)

    @Getter
    public static class PlanItem {
        private UUID assetId;         // 대상 계좌 UUID
        private String assetType;       // SPENDING / EMERGENCY / TARGET / SAVING
        private Long plannedAmount;   // 이체 금액
        private String transferScope; // PARTITION / PARKING / FOREX
    }
}
