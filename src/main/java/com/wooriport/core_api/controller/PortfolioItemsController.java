package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.portfolioItems.PortfolioItemListResponseDto;
import com.wooriport.core_api.base.dto.portfolioItems.PortfolioItemUpdateRequestDto;
import com.wooriport.core_api.base.dto.portfolioItems.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.portfolioItems.PortfolioUpdateRequestDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.PortfolioItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Portfolio Items", description = "포트폴리오 조회 및 리밸런싱")
@RestController
@RequestMapping("/api/v1/portfolios-items")
@RequiredArgsConstructor
public class PortfolioItemsController {

    private final PortfolioItemService portfolioItemService;

    // ────────────────────────────────────────────
    // GET /portfolios-items
    // 기본 포트폴리오 조회 (event_id = null, 최신)
    // ────────────────────────────────────────────
    @Operation(
            summary = "기본 포트폴리오 조회",
            description = "이벤트 없는 기본 주채예 포트폴리오를 조회합니다. (event_id = null 최신)"
    )
    @GetMapping
    public ResponseEntity<ResponseDTO<PortfolioItemListResponseDto>> getBasePortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "기본 포트폴리오 조회 성공",
                portfolioItemService.getBasePortfolios(userDetails.getUserId())));
    }

    // ────────────────────────────────────────────
    // GET /portfolios-items/{id}/portfolios
    // 목표 포트폴리오 배분 조회
    // ────────────────────────────────────────────
    @Operation(
            summary = "이벤트 포트폴리오 조회",
            description = "특정 이벤트에 연결된 주채예 포트폴리오를 조회합니다."
    )
    @GetMapping("/{eventId}")
    public ResponseEntity<ResponseDTO<PortfolioItemListResponseDto>> getEventPortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID eventId) {

        return ResponseEntity.ok(ResponseDTO.success(200, "이벤트 포트폴리오 조회 성공",
                portfolioItemService.getEventPortfolios(userDetails.getUserId(), eventId)));
    }

    // PATCH /portfolios-items
// 기본 포트폴리오 수정 (event_id = null)
    @Operation(
            summary = "기본 포트폴리오 수정",
            description = "이벤트 없는 기본 주채예 포트폴리오 비율 및 연동 계좌를 수정합니다."
    )
    @PatchMapping
    public ResponseEntity<ResponseDTO<PortfolioItemListResponseDto>> updateBasePortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PortfolioItemUpdateRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "기본 포트폴리오 수정 성공",
                portfolioItemService.updateBasePortfolios(userDetails.getUserId(), request)));
    }

    // ────────────────────────────────────────────
    // PATCH /portfolios-items/{eventId}
    // 이벤트 포트폴리오 수정
    // ────────────────────────────────────────────
    @Operation(
            summary = "이벤트 포트폴리오 수정",
            description = "이벤트 포트폴리오의 주채예 비율 및 연동 계좌를 수정합니다. 비율 합계는 100이어야 합니다."
    )
    @PatchMapping("/{eventId}")
    public ResponseEntity<ResponseDTO<PortfolioItemListResponseDto>> updateEventPortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID eventId,
            @Valid @RequestBody PortfolioItemUpdateRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "이벤트 포트폴리오 수정 성공",
                portfolioItemService.updateEventPortfolios(
                        userDetails.getUserId(), eventId, request)));
    }
}

