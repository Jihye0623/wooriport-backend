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
    private int investScore;             // 투자성향 점수 (0~7)
    private int longTermScore;           // 시간관념 점수 (0~3)

    // 축별 결과
    private String investTendency;       // 안전형 / 중립형 / 투자형
    private String timePerspective;      // 단기형 / 장기형
}