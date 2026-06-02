package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.challenge.ChallengeAdjustRequestDto;
import com.wooriport.core_api.base.dto.challenge.ChallengeCreateRequestDto;
import com.wooriport.core_api.base.dto.challenge.ChallengeProposalResponseDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.ChallengeAgentService;
import com.wooriport.core_api.service.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Challenges", description = "미니 챌린지 API")
@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;
    private final ChallengeAgentService challengeAgentService;

    @Operation(summary = "챌린지 저장", description = "프론트에서 승인한 챌린지를 IN_PROGRESS 상태로 저장합니다.")
    @PostMapping
    public ResponseEntity<ResponseDTO<UUID>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChallengeCreateRequestDto request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "챌린지 저장 성공",
                        challengeService.create(userDetails.getUserId(), request)));
    }

    @Operation(summary = "챌린지 추천", description = "지난 1개월 소비 내역과 관심 주식 테마를 기반으로 AI가 챌린지를 추천합니다.")
    @PostMapping("/recommend")
    public ResponseEntity<ResponseDTO<ChallengeProposalResponseDto>> recommend(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "챌린지 추천 성공",
                        challengeAgentService.recommend(userDetails.getUserId())));
    }

    @Operation(summary = "챌린지 난이도/주제 조정", description = "이전 제안에 대한 피드백을 반영해 챌린지를 재생성합니다.")
    @PostMapping("/adjust")
    public ResponseEntity<ResponseDTO<ChallengeProposalResponseDto>> adjust(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChallengeAdjustRequestDto request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "챌린지 조정 성공",
                        challengeAgentService.adjust(userDetails.getUserId(), request)));
    }
}
