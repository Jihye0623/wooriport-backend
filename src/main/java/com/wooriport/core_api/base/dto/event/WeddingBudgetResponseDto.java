package com.wooriport.core_api.base.dto.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeddingBudgetResponseDto {

    private WeddingBudget budget;

    @Getter
    @Builder
    public static class WeddingBudget {  // ← Budget → WeddingBudget으로 변경
        private Long venue;
        private Long honeymoon;
        private Long sdrme;
        private Long total;
    }
}