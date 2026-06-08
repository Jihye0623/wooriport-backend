package com.wooriport.core_api.base.dto.challenge;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ChallengeActiveResponseDto {

    // 진행 중 챌린지 식별자
    private UUID challengeId;

    private String title;
    private String description;
    private String category;

    // COFFEE / DELIVERY / ... (프론트 아이콘 매핑용)
    private String challengeSubType;
    // AMOUNT / COUNT
    private String challengeType;

    // 목표값(한도/횟수)
    private Long target;
    // 현재 누적값 (DB 동기화 기준)
    private Long currentValue;
    // 진행도(%) = currentValue / target * 100 (0~100)
    private int progressPercent;

    // 성공 시 보상 금액/주식
    private Long estimatedSaving;
    private String ticker;
}
