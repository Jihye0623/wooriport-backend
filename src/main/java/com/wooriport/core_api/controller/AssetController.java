package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.asset.*;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Asset", description = "자산(계좌) 연동 및 조회 관리 API")
@RequestMapping("/api/v1/assets")
@RestController
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    // POST /api/v1/assets/sync
    @Operation(summary = "마이데이터 자산 연동 (더미 데이터 생성)",
            description = "사용자의 금융 계좌 정보를 연동합니다. (현재는 마이데이터 API 연동 전이므로, 호출 시 자동으로 5개의 목적별 더미 계좌를 생성하거나 동기화합니다.)")
    @PostMapping("/sync")
    public ResponseEntity<ResponseDTO<AssetListResponseDto>> syncAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AssetListResponseDto data = assetService.syncAssets(userDetails.getUserId());

        return ResponseEntity.ok(
                ResponseDTO.success(200, "계좌 연동 성공", data));
    }

    // GET /api/v1/assets
    @Operation(summary = "전체 자산(계좌) 목록 조회",
            description = "사용자에게 연동된 모든 은행 계좌 및 자산 목록을 조회합니다. 연동된 계좌가 없으면 예외가 발생합니다.")
    @GetMapping
    public ResponseEntity<ResponseDTO<AssetListResponseDto>> getAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AssetListResponseDto data = assetService.getAssets(userDetails.getUserId());

        return ResponseEntity.ok(
                ResponseDTO.success(200, "자산 목록 조회 성공", data));
    }

    @Operation(
            summary = "급여통장 설정",
            description = """
            선택한 계좌를 급여통장으로 설정합니다.
            응답의 isWooriBank 값으로 프론트가 다음 단계를 분기합니다.
            - true  → 바로 다음 단계
            - false → 자동이체 설정 화면으로 이동
            """
    )
    @PatchMapping("/{assetId}/salary")
    public ResponseEntity<ResponseDTO<SalarySettingResponseDto>> setSalaryAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID assetId) {

        return ResponseEntity.ok(ResponseDTO.success(200, "급여통장 설정 완료",
                assetService.setSalaryAccount(userDetails.getUserId(), assetId)));
    }

    // POST /api/v1/assets/auto-transfer/connect
    @Operation(summary = "오픈뱅킹 자동이체 연결",
            description = "타행 계좌를 출금 계좌(급여 통장)로, 우리은행 계좌를 입금 계좌로 연결하여 오픈뱅킹 자동이체를 설정합니다. 연결 성공 시 출금 계좌는 자동으로 'SALARY(급여 통장)' 목적으로 지정됩니다.")
    @PostMapping("/auto-transfer/connect")
    public ResponseEntity<ResponseDTO<Void>> connectAutoTransfer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AutoTransferConnectRequestDto request) {

        assetService.connectAutoTransfer(userDetails.getUserId(), request);

        return ResponseEntity.ok(
                ResponseDTO.success(200, "자동이체 연결 성공", null));
    }

    // GET /api/v1/assets/auto-transfer/status
    @Operation(summary = "자동이체 연결 상태 및 권한 조회",
            description = "현재 사용자의 급여 통장(SALARY) 설정 여부 및 우리은행 계좌 존재 여부를 확인하여, 자동이체 활성화 상태와 이용 제한 기능 목록을 반환합니다.")
    @GetMapping("/auto-transfer/status")
    public ResponseEntity<ResponseDTO<AutoTransferStatusResponseDto>> getAutoTransferStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AutoTransferStatusResponseDto data = assetService.getAutoTransferStatus(
                userDetails.getUserId());

        return ResponseEntity.ok(
                ResponseDTO.success(200, "자동이체 상태 조회 성공", data));
    }

    // GET /api/v1/assets/summary
    @Operation(summary = "자산 카테고리별 요약 조회",
            description = "은행, 증권, 카드 등 자산 유형(AssetType)별 총액과 전체 자산 총액을 요약하여 반환합니다.")
    @GetMapping("/summary")
    public ResponseEntity<ResponseDTO<AssetSummaryResponseDto>> getAssetSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AssetSummaryResponseDto data = assetService.getAssetSummary(userDetails.getUserId());

        return ResponseEntity.ok(
                ResponseDTO.success(200, "총 자산 요약 조회 성공", data));
    }
}