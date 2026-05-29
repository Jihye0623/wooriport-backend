package com.wooriport.core_api.controller;


import com.wooriport.core_api.base.dto.portfolio.InvestAmountUpdateRequestDto;
import com.wooriport.core_api.base.dto.portfolio.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.portfolio.PortfolioUpdateRequestDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Portfolios", description = "포트폴리오 API")
@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @Operation(
            summary = "포트폴리오 조회",
            description = "사용자의 자산 배분 포트폴리오와 급여 기준 이체 금액을 반환합니다."
    )
    @GetMapping
    public ResponseEntity<ResponseDTO<PortfolioListResponseDto>> getPortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "포트폴리오 조회 성공",
                portfolioService.getPortfolios(userDetails.getUserId())));
    }

    @Operation(
            summary = "포트폴리오 저장",
            description = "자산 배분 비율 및 연동 계좌를 수정합니다. 비율 합계는 반드시 100이어야 합니다."
    )
    @PostMapping
    public ResponseEntity<ResponseDTO<PortfolioListResponseDto>> updatePortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PortfolioUpdateRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "포트폴리오 수정 성공",
                portfolioService.savePortfolios(userDetails.getUserId(), request)));
    }

    @Operation(
            summary = "포트폴리오 수정",
            description = "월 투자 금액 및 포트폴리오 자산 금액을 변경합니다. monthlyInvestAmount 변경 시 portfolioFlow.amount가 기존 비율대로 자동 재계산됩니다. " +
                          "portfolios 미전달 시 기존 portfolios 비율로 각 항목 금액을 자동 재계산합니다. 월 투자 금액은 월급을 초과할 수 없습니다."
    )
    @PatchMapping
    public ResponseEntity<ResponseDTO<PortfolioListResponseDto>> updateInvestAmount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody InvestAmountUpdateRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "투자 금액 변경 성공",
                portfolioService.updatePortfolios(userDetails.getUserId(), request)));
    }
}