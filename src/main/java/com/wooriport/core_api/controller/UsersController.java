package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.base.dto.user.UserGoalResponseDto;
import com.wooriport.core_api.base.dto.user.UserGoalUpdateRequestDto;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UsersController {
    private final UsersService usersService;

    @GetMapping("/me/goal")
    public ResponseEntity<ResponseDTO<UserGoalResponseDto>> getGoal(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ResponseDTO.success(200, "목표 조회 성공",
                usersService.getGoal(userDetails.getId())));
    }

    @PatchMapping("/me/goal")
    public ResponseEntity<ResponseDTO<Void>> updateGoal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UserGoalUpdateRequestDto request) {
        usersService.updateGoal(userDetails.getId(), request);
        return ResponseEntity.ok(ResponseDTO.success(200, "목표 수정 성공", null));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ResponseDTO<Void>> withdraw(@AuthenticationPrincipal CustomUserDetails userDetails) {
        usersService.withdraw(userDetails.getId());

        return ResponseEntity.ok(ResponseDTO.success(200, "회원 탈퇴가 정상적으로 처리되었습니다.", null));
    }
}
