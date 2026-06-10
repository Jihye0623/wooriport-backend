package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.report.ReportDetailResponseDto;
import com.wooriport.core_api.base.dto.report.ReportListResponseDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reports", description = "월간 리포트 API — 매월 1일 자정 Spring Batch 자동 생성")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ────────────────────────────────────────────
    // GET /reports
    // ────────────────────────────────────────────
    @Operation(summary = "리포트 목록 조회", description = "사용자의 전체 월간 리포트 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ResponseDTO<ReportListResponseDto>> getReports(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        ReportListResponseDto data = reportService.getReports(userDetails.getUserId());

        return ResponseEntity.ok(
                ResponseDTO.success(200, "리포트 목록 조회 성공", data));
    }

    // ────────────────────────────────────────────
    // GET /reports/{year}/{month}
    // ────────────────────────────────────────────
    @Operation(summary = "특정 월 리포트 상세 조회")
    @GetMapping("/{year}/{month}")
    public ResponseEntity<ResponseDTO<ReportDetailResponseDto>> getReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,

            @Parameter(description = "조회 연도", example = "2026")
            @PathVariable int year,

            @Parameter(description = "조회 월 1~12", example = "4")
            @PathVariable int month) {

        ReportDetailResponseDto data = reportService.getReport(
                userDetails.getUserId(), year, month);

        return ResponseEntity.ok(
                ResponseDTO.success(200, "리포트 상세 조회 성공", data));
    }
}