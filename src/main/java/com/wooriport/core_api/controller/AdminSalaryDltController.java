package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.admin.FailedSalaryActionResponseDto;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.service.AdminSalaryDltService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 관리자: 급여 DLT(재처리 끝내 실패한 이체 액션) 조회/재투입.
 * /api/v1/admin/** 은 SecurityConfiguration 에서 ROLE_ADMIN 전용.
 */
@Tag(name = "Admin - Salary DLT", description = "급여 재처리 DLT 관리(조회/재투입) API")
@RestController
@RequestMapping("/api/v1/admin/salary-dlt")
@RequiredArgsConstructor
public class AdminSalaryDltController {

    private final AdminSalaryDltService adminSalaryDltService;

    @Operation(summary = "DLT 격리 급여 실패 목록", description = "재처리 끝에 DLT 로 격리된(PENDING) 급여 실패 액션 목록")
    @GetMapping
    public ResponseEntity<ResponseDTO<List<FailedSalaryActionResponseDto>>> list() {
        return ResponseEntity.ok(ResponseDTO.success(200, "DLT 급여 실패 목록 조회 성공",
                adminSalaryDltService.listPending()));
    }

    @Operation(summary = "DLT 급여 실패 재투입(redrive)",
            description = "원인 수정 후 재투입. attempt 를 0 으로 리셋해 재처리 토픽으로 다시 보낸다(멱등).")
    @PostMapping("/{id}/redrive")
    public ResponseEntity<ResponseDTO<Void>> redrive(@PathVariable UUID id) {
        adminSalaryDltService.redrive(id);
        return ResponseEntity.ok(ResponseDTO.success(200, "재투입 완료 (attempt 0 리셋)", null));
    }
}
