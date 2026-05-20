package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
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
            summary = "porTI 유형 저장",
            description = "porTI 검사 완료 후 결과 유형을 저장합니다. (SWIMMING/ARCHERY/JUDO/RHYTHMIC/FENCING/CYCLING)"
    )
    @PatchMapping("/porti-type")
    public ResponseEntity<ResponseDTO<Void>> updatePortiType(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PortiTypeUpdateRequestDto request) {

        usersService.updatePortiType(userDetails.getUserId(), request.getPortiType());

        return ResponseEntity.ok(ResponseDTO.success(200, "porTI 유형 저장 성공", null));
    }
}
