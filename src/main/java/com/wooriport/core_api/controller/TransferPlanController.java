package com.wooriport.core_api.controller;


import com.wooriport.core_api.base.dto.asset.ScheduledDateRequestDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.base.dto.transfer.TransferPlanCreateRequestDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanListResponseDto;
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

import java.util.UUID;

@Tag(name = "Transfer Plan", description = "이체(리밸런싱) 계획 관리 API")
@RestController
@RequestMapping("/api/v1/transfer-plans")
@RequiredArgsConstructor
public class TransferPlanController {

    private final TransferPlanService transferPlanService;
    private final AssetService assetService;

    /**
     * GET /api/v1/transfer-plans?year=2025&month=5
     * 특정 연/월의 이체 계획 목록 조회
     */
    @Operation(summary = "월별 이체 계획 조회", description = "특정 연도와 월에 해당하는 이체(리밸런싱) 계획 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ResponseDTO<TransferPlanListResponseDto>> getTransferPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        TransferPlanListResponseDto data = transferPlanService.getTransferPlans(
                userDetails.getUserId(), year, month);

        return ResponseEntity.ok(
                ResponseDTO.success(200, "이체 계획 목록 조회 성공", data));
    }

    /**
     * POST /api/v1/transfer-plans
     * 월 이체 계획 생성 (비율 합산 100% 검증)
     */
    @Operation(summary = "월 이체 계획 일괄 생성", description = "초기 온보딩 또는 새 목표 추가 시, 비율 합산 100% 검증을 거쳐 월 이체 계획들을 일괄 생성합니다.")
    @PostMapping
    public ResponseEntity<ResponseDTO<TransferPlanListResponseDto>> createTransferPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody TransferPlanCreateRequestDto request) {

        TransferPlanListResponseDto data = transferPlanService.createTransferPlans(
                userDetails.getUserId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "이체 계획 생성 성공", data));
    }

    /**
     * PATCH /api/v1/transfer-plans/{id}
     * 이체 계획 단건 수정 (금액 변경 시 is_confirmed 자동 FALSE)
     */
    @Operation(summary = "이체 계획 단건 금액 수정", description = "특정 이체 계획의 금액을 수정합니다. AI 제안 금액을 사용자가 변경할 때 호출되며, 금액 변경 시 확정 상태(is_confirmed)가 자동으로 대기(FALSE) 상태로 전환됩니다.")
    @PatchMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> updateTransferPlan(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @RequestBody TransferPlanUpdateRequestDto request) {

        transferPlanService.updateTransferPlan(userDetails.getUserId(), id, request);

        return ResponseEntity.ok(
                ResponseDTO.success(200, "이체 계획 수정 성공", null));
    }

    /**
     * POST /api/v1/transfer-plans/confirm-all?year=2025&month=5
     * 이체 계획 전체 확인 (is_confirmed = TRUE)
     */
    @Operation(summary = "이체 계획 전체 일괄 확정", description = "지정된 연/월의 모든 이체 계획을 확정(is_confirmed = TRUE) 처리합니다. 이 처리가 완료되어야 실제 스케줄러가 이체를 실행할 수 있습니다.")
    @PostMapping("/confirm-all")
    public ResponseEntity<ResponseDTO<Void>> confirmAll(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        transferPlanService.confirmAll(userDetails.getUserId(), year, month);

        return ResponseEntity.ok(
                ResponseDTO.success(200, "이체 계획 확인 완료", null));
    }

    @Operation(summary = "자동이체 실행일(월급날) 설정",
            description = "매월 자동 리밸런싱(이체)이 실행될 날짜를 지정합니다. Users 테이블의 salary_date 값을 업데이트합니다.")
    @PatchMapping("/scheduled-date")
    public ResponseEntity<ResponseDTO<Void>> updateScheduledDate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ScheduledDateRequestDto request) {

        assetService.updateScheduledDate(userDetails.getUserId(), request);

        return ResponseEntity.ok(
                ResponseDTO.success(200, "자동이체 실행일 설정 성공", null));
    }
}