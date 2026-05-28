package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.event.EventCreateRequestDto;
import com.wooriport.core_api.base.dto.event.EventDetailResponseDto;
import com.wooriport.core_api.base.dto.event.EventListResponseDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @Operation(summary = "이벤트 저장", description = "AI가 구체화한 목표를 사용자가 확정하면 이벤트로 저장합니다.")
    @PostMapping
    public ResponseEntity<ResponseDTO<UUID>> createEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody EventCreateRequestDto request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "이벤트 저장 성공",
                        eventService.createEvent(userDetails.getUserId(), request)));
    }

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
