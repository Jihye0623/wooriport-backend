package com.wooriport.core_api.base.dto.Notification;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SpendingTrendDetailResponseDto {

    private String aiComment;
    private List<WeeklyItem> weeklyGraph;
    private List<CategoryItem> categorySpending;

    @Getter
    @Builder
    public static class WeeklyItem {
        private int week;
        private Long lastMonth;
        private Long thisMonth;
    }

    @Getter
    @Builder
    public static class CategoryItem {
        private String category;
        private Long amount;
    }
}
