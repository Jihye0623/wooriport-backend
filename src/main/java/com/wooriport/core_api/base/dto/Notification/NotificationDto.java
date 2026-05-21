package com.wooriport.core_api.base.dto.Notification;
import com.wooriport.core_api.domain.Notifications;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationDto {

    @Getter
    @AllArgsConstructor
    public static class Response {
        private UUID id;
        private Notifications.NotificationType type;
        private String title;
        private String content;
        private Boolean isRead;
        private LocalDateTime sentAt;

        public static Response from(Notifications notification) {
            return new Response(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getContent(),
                    notification.getIsRead(),
                    notification.getSentAt()
            );
        }
    }
}