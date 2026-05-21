package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notifications, UUID> {
    List<Notifications> findByUserIdOrderBySentAtDesc(UUID userId);

    Optional<Notifications> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notifications n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") UUID userId);

    // 안읽은 알림 전체 (전체 읽음 처리용)
    @Query("""
        SELECT n FROM Notifications n
        WHERE n.user.id = :userId
          AND n.isRead = false
        """)
    List<Notifications> findUnreadByUserId(@Param("userId") UUID userId);
}