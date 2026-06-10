package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.consultant.*;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.ConsultantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Consultant", description = "AI 컨설턴트 API")
@RestController
@RequestMapping("/api/v1/consultant")
@RequiredArgsConstructor
public class ConsultantController {

    private final ConsultantService consultantService;

    @Operation(
            summary = "목표 분석",
            description = "사용자 목표를 분석해 salary/portfolio 재설정 방향을 결정합니다. " +
                    "JWT에서 userId 추출 후 백엔드가 대시보드 스냅샷을 구성해 AI 서버에 전달합니다."
    )
    @PostMapping("/analyze")
    public ResponseEntity<ResponseDTO<ConsultantAnalyzeResponseDto>> analyze(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ConsultantAnalyzeRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "목표 분석 성공",
                consultantService.analyze(userDetails.getUserId(), request)));
    }

    @Operation(
            summary = "재설정 제안 생성",
            description = "분석 결과(action) 기반으로 재설정 제안을 생성합니다. " +
                    "백엔드가 대시보드 스냅샷을 구성해 AI 서버에 전달합니다."
    )
    @PostMapping("/propose")
    public ResponseEntity<ResponseDTO<ConsultantProposeResponseDto>> propose(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ConsultantProposeRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "재설정 제안 성공",
                consultantService.propose(userDetails.getUserId(), request)));
    }

    @Operation(
            summary = "AI 제안 적용",
            description = "AI 제안 결과를 포트폴리오/투자 금액에 반영합니다."
    )
    @PostMapping("/apply")
    public ResponseEntity<ResponseDTO<Void>> apply(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ConsultantApplyRequestDto request) {

        consultantService.apply(userDetails.getUserId(), request);
        return ResponseEntity.ok(ResponseDTO.success(200, "재설정 적용 성공", null));
    }
}
