package com.wooriport.core_api.base.dto.event;

import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class EventDetailResponseDto {
    private UUID id;
    private String title;
    private Long targetAmount;
    private Long currentAmount;
    private int achievementRate;     // 달성률 (%)
    private String deadline;
    private String status;           // ACTIVE / COMPLETED / CANCELLED / EXPIRED
    private String eventDescription; // 자연어 원문
    private Boolean isShortTerm;     // 6개월 이하 여부
}