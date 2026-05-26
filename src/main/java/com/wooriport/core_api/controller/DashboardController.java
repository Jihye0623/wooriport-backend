package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.dashboard.DashboardResponseDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "메인 대시보드 조회 API")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "메인 대시보드 조회",
            description = "사용자, 자산 요약, 월급 분배, 이벤트, 소비 현황, 포트폴리오를 한 번에 반환합니다."
    )
    @GetMapping
    public ResponseEntity<ResponseDTO<DashboardResponseDto>> getDashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "메인 대시보드 조회 성공",
                dashboardService.getDashboard(userDetails.getUserId())));
    }
}
