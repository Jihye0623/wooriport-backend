package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.base.dto.event.*;
import com.wooriport.core_api.service.EventAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Goal Agent",
        description = """
        목표 설정 흐름
        STEP 2. /wedding · /travel · /purchase → 카테고리별 예상 금액 반환
        STEP 4. /portfolio → 투자금 기반 포트폴리오 구성 비율 추천
        STEP 6. /analysis  → AI 심층 진단 리포트
        """
)
@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class EventAgentController {

    private final EventAgentService eventAgentService;

    // ────────────────────────────────────────────
    // STEP 2-2. 결혼 옵션 → 예산 산정
    // ────────────────────────────────────────────
    @Operation(
            summary = "[STEP 2] 결혼 예산 산정",
            description = "결혼 지역·시기·신혼여행·스드메 규모 입력 → 항목별 예상 비용 반환"
    )
    @PostMapping("/wedding")
    public ResponseEntity<ResponseDTO<WeddingBudgetResponseDto>> estimateWedding(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody WeddingRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "결혼 예산 산정 성공",
                eventAgentService.estimateWedding(userDetails.getUserId(), request)));
    }

    // ────────────────────────────────────────────
    // STEP 2-2. 여행 옵션 → 예산 산정
    // ────────────────────────────────────────────
    @Operation(
            summary = "[STEP 2] 여행 예산 산정",
            description = "여행지·스타일·기간 입력 → 항목별 예상 비용 반환"
    )
    @PostMapping("/travel")
    public ResponseEntity<ResponseDTO<TravelBudgetResponseDto>> estimateTravel(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TravelRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "여행 예산 산정 성공",
                eventAgentService.estimateTravel(userDetails.getUserId(), request)));
    }

    // ────────────────────────────────────────────
    // STEP 2-2. 기타 구매 → 상품 후보 조회
    // ────────────────────────────────────────────
    @Operation(
            summary = "[STEP 2] 구매 물건 후보 조회",
            description = "구매 희망 물건명 입력 → 상품 후보 목록·예상 가격 반환"
    )
    @PostMapping("/purchase")
    public ResponseEntity<ResponseDTO<PurchaseResponseDto>> searchPurchase(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PurchaseRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "구매 후보 조회 성공",
                eventAgentService.searchPurchase(userDetails.getUserId(), request)));
    }

    // ────────────────────────────────────────────
    // STEP 4. 투자금 → 포트폴리오 비율 추천
    // ────────────────────────────────────────────
    @Operation(
            summary = "[STEP 4] 포트폴리오 비율 추천",
            description = "초기 자본금·월 투자금·목표 금액 입력 → 현금/주식/채권 추천 비율 반환"
    )
    @PostMapping("/portfolio")
    public ResponseEntity<ResponseDTO<PortfolioResponseDto>> createPortfolio(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PortfolioRequestDto request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "포트폴리오 추천 성공",
                        eventAgentService.createPortfolio(userDetails.getUserId(), request)));
    }

    // ────────────────────────────────────────────
    // STEP 6. 포트폴리오 심층 진단
    // ────────────────────────────────────────────
    @Operation(
            summary = "[STEP 6] AI 심층 진단",
            description = "사용자가 확정한 주식/채권/현금 비율 입력 → AI 심층 진단 리포트 반환"
    )
    @PostMapping("/analysis")
    public ResponseEntity<ResponseDTO<AnalysisResponseDto>> analyzePortfolio(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AnalysisRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "심층 진단 성공",
                eventAgentService.analyzePortfolio(userDetails.getUserId(), request)));
    }

    // ────────────────────────────────────────────
    // STEP 7. 포트폴리오 확정
    // ────────────────────────────────────────────
    @Operation(summary = "[STEP 7] 포트폴리오 확정 저장")
    @PostMapping("/confirm")
    public ResponseEntity<ResponseDTO<Void>> confirmGoal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody GoalConfirmRequestDto request) {

        eventAgentService.confirmGoal(userDetails.getUserId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "포트폴리오 확정 완료", null));
    }
}