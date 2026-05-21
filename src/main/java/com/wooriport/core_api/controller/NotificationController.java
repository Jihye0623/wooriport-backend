package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.Notification.NotificationDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Notification", description = "알림 관련 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회", description = "내 계정으로 수신된 모든 알림 목록을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ResponseDTO<List<NotificationDto.Response>>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<NotificationDto.Response> responses = notificationService.getNotifications(userDetails.getId());
        return ResponseEntity.ok(ResponseDTO.success(200, "알림 목록 조회 성공", responses));
    }

    @Operation(summary = "알림 단건 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ResponseDTO<Void>> readNotification(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        notificationService.readNotification(userDetails.getId(), id);
        return ResponseEntity.ok(ResponseDTO.success(200, "알림 읽음 처리 성공", null));
    }

    @Operation(summary = "알림 전체 읽음 처리", description = "안 읽은 모든 알림을 한 번에 읽음 상태로 변경합니다.")
    @PatchMapping("/read-all")
    public ResponseEntity<ResponseDTO<Void>> readAllNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        notificationService.readAllNotifications(userDetails.getId());
        return ResponseEntity.ok(ResponseDTO.success(200, "전체 알림 읽음 처리 성공", null));
    }
}