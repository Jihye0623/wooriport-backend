package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.Auth.AuthDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ResponseDTO> signup(@Valid @RequestBody AuthDto.SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(201)
                .body(ResponseDTO.success(201, "회원가입이 완료되었습니다.", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseDTO> login(@RequestBody AuthDto.LoginRequest request) {
        AuthDto.TokenResponse tokenResponse = authService.login(request);
        return ResponseEntity.ok(ResponseDTO.success(200, "로그인에 성공했습니다.", tokenResponse));
    }
}
