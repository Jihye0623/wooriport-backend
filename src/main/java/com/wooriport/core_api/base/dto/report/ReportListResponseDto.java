package com.wooriport.core_api.base.dto.report;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

// ─────────────────────────────────────────
// GET /reports 응답
// ─────────────────────────────────────────
@Getter
@Builder
public class ReportListResponseDto {

    private List<ReportItem> reports;
    private int totalCount;

    @Getter
    @Builder
    public static class ReportItem {
        private UUID id;
        private int year;
        private int month;
        private Long totalIncome;
        private Long totalExpense;
        private Long surplus;
        private String createdAt;
    }
}

