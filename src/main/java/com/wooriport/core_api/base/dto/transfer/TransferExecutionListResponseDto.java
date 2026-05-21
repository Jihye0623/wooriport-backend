package com.wooriport.core_api.base.dto.transfer;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class TransferExecutionListResponseDto {
    private List<ExecutionItem> executions;
    private int totalCount;
    private Long totalAmount;

    @Getter
    @Builder
    public static class ExecutionItem {
        private UUID id;
        private String fromInstitution;
        private String toInstitution;
        private String assetType;
        private Long amount;
        private String status;          // PENDING / COMPLETED / FAILED
        private LocalDateTime executedAt;
    }
}
