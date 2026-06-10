package com.wooriport.core_api.base.dto.consultant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConsultantAnalyzeResponseDto {
    private String action;    // "salary" | "portfolio"
    private String reasoning;
    private boolean hasInvestmentFlows;
}
