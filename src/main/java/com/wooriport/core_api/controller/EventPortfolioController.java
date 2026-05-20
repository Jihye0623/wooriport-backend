package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.eventPortfolio.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.eventPortfolio.PortfolioUpdateRequestDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.EventPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Event Portfolio", description = "목표 포트폴리오 조회 및 리밸런싱")
@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class EventPortfolioController {

    private final EventPortfolioService eventPortfolioService;

    // ────────────────────────────────────────────
    // GET /event/{id}/portfolios
    // 목표 포트폴리오 배분 조회
    // ────────────────────────────────────────────
    @Operation(
            summary = "목표 포트폴리오 조회",
            description = "목표에 연결된 주식/채권/예금 비율 및 계좌 연동 현황을 조회합니다."
    )
    @GetMapping("/{eventId}/portfolios")
    public ResponseEntity<ResponseDTO<PortfolioListResponseDto>> getPortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID eventId) {

        return ResponseEntity.ok(ResponseDTO.success(200, "포트폴리오 조회 성공",
                eventPortfolioService.getPortfolios(userDetails.getUserId(), eventId)));
    }

    // ────────────────────────────────────────────
    // PATCH /event/{id}/portfolios
    // 목표 포트폴리오 비율 수정 (리밸런싱)
    // ────────────────────────────────────────────
    @Operation(
            summary = "목표 포트폴리오 리밸런싱",
            description = "주식/채권/예금 비율 및 연동 계좌를 수정합니다. 비율 합계는 100이어야 합니다."
    )
    @PatchMapping("/{eventId}/portfolios")
    public ResponseEntity<ResponseDTO<PortfolioListResponseDto>> updatePortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID eventId,
            @Valid @RequestBody PortfolioUpdateRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "포트폴리오 수정 성공",
                eventPortfolioService.updatePortfolios(
                        userDetails.getUserId(), eventId, request)));
    }
}

