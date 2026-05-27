package com.wooriport.core_api.base.dto.event;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class EventListResponseDto {
    private List<EventItem> events;
    private int totalCount;

    @Getter
    @Builder
    public static class EventItem {
        private UUID id;
        private String title;
        private Long targetAmount;
        private Long currentAmount;
        private int achievementRate;   // 달성률 (%)
        private String deadline;
        private String status;
    }
}