package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.agent.*;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Agent", description = "AI Agent API")
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(
            summary = "AI 진단 리포트 생성",
            description = """
            porTI 설문 답변을 받아 유형을 계산·저장하고
            3개월 소비 데이터, 투자 성향, 저축 목록을 FastAPI로 전달해
            AI 코멘트 3개를 생성합니다.
            """
    )
    @PostMapping("/profile")
    public ResponseEntity<ResponseDTO<AgentProfileResponseDto>> generateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AgentProfileRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "AI 진단 리포트 생성 성공",
                agentService.generateProfile(userDetails.getUserId(), request)));
    }


    @Operation(
            summary = "월급 리밸런싱 추천",
            description = """
        JWT에서 userId 추출 후 백엔드가 데이터 수집.
        월급, 고정지출, 3개월 소비, 보유 계좌, porTI를 FastAPI로 전달해
        월급 리밸런싱 계획을 추천받아 반환합니다.
        """
    )
    @PostMapping("/rebalance")
    public ResponseEntity<ResponseDTO<AgentRecommendResponseDto>> recommend(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "월급 리밸런싱 추천 성공",
                agentService.recommend(userDetails.getUserId())));
    }

    @Operation(
            summary = "AI 자산 처방전 생성",
            description = """
            PrescriptionComplete 화면 진입 시 호출됩니다.
            사용자의 invest_amount / porti / 보유 자산 / 상품 카탈로그를 FastAPI(/asset-portfolio)에 넘겨
            investment_flows를 생성받아 portfolio_flows + portfolio_flow_items 테이블에 저장합니다.
            """
    )
    @PostMapping("/prescriptions")
    public ResponseEntity<ResponseDTO<Void>> generatePrescriptions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        agentService.generatePrescriptions(userDetails.getUserId());
        return ResponseEntity.ok(ResponseDTO.success(200,
                "AI 포트폴리오 분석 및 생성 완료", null));
    }

}