package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.base.dto.transfer.TransferExecuteResultDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanListResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanSummaryResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanUpdateRequestDto;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.AssetService;
import com.wooriport.core_api.service.TransferPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Transfer Plan", description = "이체(리밸런싱) 계획 관리 API")
@RestController
@RequestMapping("/api/v1/transfer-plans")
@RequiredArgsConstructor
public class TransferPlanController {

    private final TransferPlanService transferPlanService;
    private final AssetService assetService;

    @Operation(
            summary = "급여 기반 이체 계획 자동 생성",
            description = """
        최근 급여 트랜잭션 금액을 기준으로
        portfolios 비율에 따라 이체 계획을 자동 생성하고
        사용자에게 알림을 발송합니다.
        """
    )
    @PostMapping("/generate")
    public ResponseEntity<ResponseDTO<TransferPlanListResponseDto>> generatePlans(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "이체 계획 생성 및 알림 발송 완료",
                        transferPlanService.generateFromSalary(userDetails.getUserId())));
    }

    /**
     * GET /api/v1/transfer-plans?year=2025&month=5
     * 특정 연/월의 이체 계획 목록 조회
     */
    @Operation(summary = "월별 이체 계획 조회", description = "이번달 월급 기준으로 portfolios / flow 분배 금액과 기준값 대비 diff를 반환합니다.")
    @GetMapping
    public ResponseEntity<ResponseDTO<TransferPlanSummaryResponseDto>> getTransferPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        return ResponseEntity.ok(ResponseDTO.success(200, "이체 계획 조회 성공",
                transferPlanService.getTransferPlans(userDetails.getUserId(), year, month)));
    }

    /**
     * PATCH /api/v1/transfer-plans?year=2025&month=5
     * 이체 계획 일괄 수정 (assetId + amount 쌍 리스트)
     */
    @Operation(summary = "이체 계획 금액 일괄 수정", description = "assetId와 수정 금액 쌍의 리스트를 받아 해당 월 이체 계획을 일괄 수정합니다. 금액 변경 시 is_confirmed가 자동으로 FALSE로 전환됩니다.")
    @PatchMapping
    public ResponseEntity<ResponseDTO<Void>> updateTransferPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month,
            @RequestBody @Valid List<TransferPlanUpdateRequestDto> requests) {

        transferPlanService.updateTransferPlans(userDetails.getUserId(), year, month, requests);

        return ResponseEntity.ok(
                ResponseDTO.success(200, "이체 계획 수정 성공", null));
    }

    /**
     * POST /api/v1/transfer-plans/confirm-all?year=2025&month=5
     * 이체 계획 전체 확인 (is_confirmed = TRUE)
     */
    @Operation(
            summary = "이체 계획 확인 및 즉시 실행",
            description = "이체 계획을 확인하고 즉시 실행합니다."
    )
    @PostMapping("/confirm-all")
    public ResponseEntity<ResponseDTO<TransferExecuteResultDto>> confirmAndExecute(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        return ResponseEntity.ok(ResponseDTO.success(200, "이체 실행 완료",
                transferPlanService.confirmAndExecute(userDetails.getUserId(), year, month)));
    }

}