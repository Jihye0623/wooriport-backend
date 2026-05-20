package com.wooriport.core_api.base.dto.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TravelBudgetResponseDto {

    private TravelBudget budget;  // ← Budget → TravelBudget으로 변경

    @Getter
    @Builder
    public static class TravelBudget {
        private Long accommodation;
        private Long flight;
        private Long food;
        private Long transportation;
        private Long sightseeing;
        private Long total;
    }
}