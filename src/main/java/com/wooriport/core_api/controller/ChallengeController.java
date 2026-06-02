package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.challenge.ChallengeCreateRequestDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.config.security.CustomUserDetails;
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

    @Operation(
            summary = "챌린지 저장",
            description = "프론트에서 승인한 챌린지를 IN_PROGRESS 상태로 저장합니다."
    )
    @PostMapping
    public ResponseEntity<ResponseDTO<UUID>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChallengeCreateRequestDto request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(201, "챌린지 저장 성공",
                        challengeService.create(userDetails.getUserId(), request)));
    }
}
