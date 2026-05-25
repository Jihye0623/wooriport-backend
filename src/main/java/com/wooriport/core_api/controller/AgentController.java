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
    @PostMapping("/recommend")
    public ResponseEntity<ResponseDTO<AgentRecommendResponseDto>> recommend(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "월급 리밸런싱 추천 성공",
                agentService.recommend(userDetails.getUserId())));
    }

    @Operation(
            summary = "이벤트 기반 리밸런싱 재추천",
            description = "자연어 이벤트 목표 입력 시 기존 포트폴리오 대비 diff 포함해서 재추천합니다."
    )
    @PostMapping("/input")
    public ResponseEntity<ResponseDTO<AgentInputResponseDto>> input(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AgentInputRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "이벤트 기반 리밸런싱 재추천 성공",
                agentService.input(userDetails.getUserId(), request)));
    }


//    // ────────────────────────────────────────────
//    // STEP 6. 포트폴리오 심층 진단
//    // ────────────────────────────────────────────
//    @Operation(
//            summary = "[STEP 6] AI 심층 진단",
//            description = "사용자가 확정한 주식/채권/현금 비율 입력 → AI 심층 진단 리포트 반환"
//    )
//    @PostMapping("/analysis")
//    public ResponseEntity<ResponseDTO<AnalysisResponseDto>> analyzePortfolio(
//            @AuthenticationPrincipal CustomUserDetails userDetails,
//            @Valid @RequestBody AnalysisRequestDto request) {
//
//        return ResponseEntity.ok(ResponseDTO.success(200, "심층 진단 성공",
//                agentService.analyzePortfolio(userDetails.getUserId(), request)));
//    }
//
//    // ────────────────────────────────────────────
//    // STEP 7. 포트폴리오 확정
//    // ────────────────────────────────────────────
//    @Operation(summary = "[STEP 7] 포트폴리오 확정 저장")
//    @PostMapping("/confirm")
//    public ResponseEntity<ResponseDTO<Void>> confirmGoal(
//            @AuthenticationPrincipal CustomUserDetails userDetails,
//            @Valid @RequestBody GoalConfirmRequestDto request) {
//
//        agentService.confirmGoal(userDetails.getUserId(), request);
//
//        return ResponseEntity.status(HttpStatus.CREATED)
//                .body(ResponseDTO.success(201, "포트폴리오 확정 완료", null));
//    }
}