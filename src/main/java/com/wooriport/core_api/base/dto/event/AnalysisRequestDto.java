package com.wooriport.core_api.base.dto.event;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AnalysisRequestDto {

    @NotNull
    private PortfolioUser portfolioUser;

    @Getter
    public static class PortfolioUser {
        @NotNull private Integer cashRatio;     // 현금 비율
        @NotNull private Integer stockRatio;    // 주식 비율
        @NotNull private Integer bondRatio;     // 채권 비율
    }
}
