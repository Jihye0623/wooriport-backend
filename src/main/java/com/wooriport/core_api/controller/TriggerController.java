package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.batch.scheduler.ChallengeScheduler;
import com.wooriport.core_api.base.batch.scheduler.SalaryTransferScheduler;
import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.repository.UserRepository;
import com.wooriport.core_api.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trigger", description = "테스트 전용 수동 트리거 — 인증 불필요")
@RestController
@RequestMapping("/api/v1/trigger")
@RequiredArgsConstructor
public class TriggerController {

    private final ChallengeScheduler challengeScheduler;
    private final SalaryTransferScheduler salaryTransferScheduler;
    private final ReportService reportService;
    private final UserRepository userRepository;

    @Value("${demo.email:}")
    private String demoEmail;

    @Operation(summary = "[TEST] 만료 챌린지 수동 판정")
    @PostMapping("/challenge")
    public ResponseEntity<ResponseDTO<Void>> challenge() {
        challengeScheduler.checkExpiredChallenges();
        return ResponseEntity.ok(ResponseDTO.success(200, "만료 챌린지 판정 완료", null));
    }

    @Operation(summary = "[TEST] 급여 자동이체 수동 실행")
    @PostMapping("/salary")
    public ResponseEntity<ResponseDTO<Void>> salary() {
        salaryTransferScheduler.run();
        return ResponseEntity.ok(ResponseDTO.success(200, "급여 자동이체 실행 완료", null));
    }

    @Operation(summary = "[TEST] 월간 리포트 수동 생성")
    @PostMapping("/report/{year}/{month}")
    public ResponseEntity<ResponseDTO<Void>> report(
            @PathVariable int year,
            @PathVariable int month) {
        reportService.generateMonthlyReportForAll(year, month);
        return ResponseEntity.ok(ResponseDTO.success(200, "리포트 생성 완료", null));
    }

    @Operation(summary = "[TEST] 데모 유저 삭제 — 재가입 시 더미 데이터 자동 시딩")
    @PostMapping("/reset-demo")
    public ResponseEntity<ResponseDTO<String>> resetDemo() {
        if (demoEmail.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ResponseDTO.fail(400, "demo.email 환경변수 미설정"));
        }
        userRepository.findByEmail(demoEmail).ifPresent(user -> userRepository.delete(user));
        return ResponseEntity.ok(ResponseDTO.success(200, "데모 유저 삭제 완료", demoEmail));
    }
}
