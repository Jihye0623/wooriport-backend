package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.base.dto.user.PortiSurveyRequestDto;
import com.wooriport.core_api.base.dto.user.PortiSurveyResultDto;
import com.wooriport.core_api.base.dto.user.PortiTypeUpdateRequestDto;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.UsersService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UsersController {
    private final UsersService usersService;

    @DeleteMapping("/me")
    public ResponseEntity<ResponseDTO<Void>> withdraw(@AuthenticationPrincipal CustomUserDetails userDetails) {
        usersService.withdraw(userDetails.getId());

        return ResponseEntity.ok(ResponseDTO.success(200, "회원 탈퇴가 정상적으로 처리되었습니다.", null));
    }

    @Operation(
            summary = "porTI 설문 제출 및 유형 계산",
            description = """
            10개 문항 답변을 받아 3개 축(투자성향/관리스타일/시간관념)을 채점하고
            최종 porTI 유형을 계산해 users.porti_type에 저장합니다.
            """
    )
    @PostMapping("/porti-survey")
    public ResponseEntity<ResponseDTO<PortiSurveyResultDto>> submitSurvey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PortiSurveyRequestDto request) {

        return ResponseEntity.ok(ResponseDTO.success(200, "porTI 검사 완료",
                usersService.calculateAndSave(userDetails.getUserId(), request)));
    }
}
