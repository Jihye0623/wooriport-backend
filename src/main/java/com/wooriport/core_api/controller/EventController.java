package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.event.EventDetailResponseDto;
import com.wooriport.core_api.base.dto.event.EventListResponseDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Events", description = "이벤트 API")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "이벤트 목록 조회")
    @GetMapping
    public ResponseEntity<ResponseDTO<EventListResponseDto>> getEvents(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "이벤트 목록 조회 성공",
                eventService.getEvents(userDetails.getUserId())));
    }

    @Operation(
            summary = "이벤트 상세 조회",
            description = "이벤트 ID로 단건 상세 정보를 조회합니다."
    )
    @GetMapping("/{eventId}")
    public ResponseEntity<ResponseDTO<EventDetailResponseDto>> getEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID eventId) {

        return ResponseEntity.ok(ResponseDTO.success(200, "이벤트 상세 조회 성공",
                eventService.getEvent(userDetails.getUserId(), eventId)));
    }
}
