package com.wooriport.core_api.base.dto.Notification;


import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class NotificationListResponseDto {

    private List<NotificationItem> notifications;
    private int unreadCount;

    @Getter
    @Builder
    public static class NotificationItem {
        private UUID id;
        private String type;     // EXPENSE_ALERT / SALARY_REBALANCING / REPORT_READY
        private String title;
        private String content;
        private Boolean isRead;
        private String sentAt;
    }
}
