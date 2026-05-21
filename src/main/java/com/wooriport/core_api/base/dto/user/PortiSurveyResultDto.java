package com.wooriport.core_api.base.dto.user;

import com.wooriport.core_api.domain.Users;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PortiSurveyResultDto {

    private Users.PortiType portiType;   // 최종 유형 (SWIMMING 등)
    private String typeName;             // 유형 이름 (수영)
    private String description;          // 유형 설명

    // 축별 점수 (참고용)
    private int investScore;             // 축 A: 투자형 점수 (0~5)
    private int activeScore;             // 축 B: 능동형 점수 (0~4)
    private int longTermScore;           // 축 C: 장기형 점수 (0~3)

    // 축별 결과
    private String investTendency;       // 안전형 / 투자형
    private String managementStyle;      // 시스템형 / 능동형
    private String timePerspective;      // 단기형 / 장기형
}