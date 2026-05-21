package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.Notification.NotificationDto;
import com.wooriport.core_api.domain.Notifications;
import com.wooriport.core_api.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationDto.Response> getNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderBySentAtDesc(userId).stream()
                .map(NotificationDto.Response::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void readNotification(UUID userId, UUID notificationId) {
        Notifications notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 권한이 없는 알림입니다."));

        notification.markAsRead();
    }

    @Transactional
    public void readAllNotifications(UUID userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
}