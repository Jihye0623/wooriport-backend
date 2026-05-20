package com.wooriport.core_api.base.dto.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisResponseDto {
    private String analysisReport;  // 심층 진단 리포트
    private String summary;         // 요약
}