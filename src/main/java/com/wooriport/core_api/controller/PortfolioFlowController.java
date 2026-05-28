package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.portfolioFlow.AvailableAssetListResponseDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowUpdateRequestDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.PortfolioFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Portfolio Flows", description = "asset-portfolio 화면용 흐름(끌어오기·모으기·넣기) 조회")
@RestController
@RequestMapping("/api/v1/portfolio-flows")
@RequiredArgsConstructor
public class PortfolioFlowController {

    private final PortfolioFlowService portfolioFlowService;

    @Operation(
            summary = "흐름 목록 조회",
            description = "사용자의 모든 포트폴리오 흐름(기본 + 이벤트)과 각 흐름의 모으기 통장, 끌어오기 항목(PULL), 넣기 항목(PUT)을 한 번에 반환합니다."
    )
    @GetMapping
    public ResponseEntity<ResponseDTO<PortfolioFlowListResponseDto>> getFlows(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "포트폴리오 흐름 조회 성공",
                portfolioFlowService.getFlows(userDetails.getUserId())));
    }

    @Operation(
            summary = "흐름에서 사용 가능한 통장 목록",
            description = "끌어오기/모으기 단계의 통장 후보 풀. 카드(CREDIT/DEBIT) 와 soft-delete 만 제외하고 반환합니다. 현재 흐름 state 기준 중복 제거는 클라이언트에서 동적으로 처리합니다."
    )
    @GetMapping("/available-assets")
    public ResponseEntity<ResponseDTO<AvailableAssetListResponseDto>> getAvailableAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "사용 가능한 통장 조회 성공",
                portfolioFlowService.getAvailableAssets(userDetails.getUserId())));
    }

    @Operation(
            summary = "흐름 수정",
            description = "지정한 흐름의 모으기 통장(gathering)·끌어오기 항목(PULL)·넣기 항목(PUT) 을 일괄 교체합니다. 기존 items 는 삭제 후 재생성됩니다."
    )
    @PatchMapping("/{flowId}")
    public ResponseEntity<ResponseDTO<PortfolioFlowListResponseDto.FlowDto>> updateFlow(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID flowId,
            @Valid @RequestBody PortfolioFlowUpdateRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "흐름 수정 성공",
                portfolioFlowService.updateFlow(userDetails.getUserId(), flowId, request)));
    }
}
