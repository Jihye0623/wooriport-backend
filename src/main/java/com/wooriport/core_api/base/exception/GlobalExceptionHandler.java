package com.wooriport.core_api.base.exception;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 — 유효성 검사 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        log.warn("[400] 유효성 검사 실패: {}", message);
        return ResponseEntity.badRequest()
                .body(ResponseDTO.fail(400, message));
    }

    // 400 — 잘못된 요청
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[400] 잘못된 요청: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ResponseDTO.fail(400, e.getMessage()));
    }

    // 400 — 요청 바디 파싱 실패 (잘못된 UUID 형식 등)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[400] 요청 파싱 실패: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ResponseDTO.fail(400, "요청 형식이 올바르지 않습니다."));
    }

    // 404 — 리소스 없음
    @ExceptionHandler({
            AssetNotFoundException.class,
            UserNotFoundException.class,
            TransferPlanNotFoundException.class
    })
    public ResponseEntity<?> handleNotFound(RuntimeException e) {
        log.warn("[404] 리소스 없음: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResponseDTO.fail(404, e.getMessage()));
    }

    // 409 — 비즈니스 규칙 위반 (상태 오류)
    @ExceptionHandler({
            PortfolioNotSetException.class,
            PortiNotSetException.class,
            SalaryNotFoundException.class,
            AssetDeletionNotAllowedException.class
    })
    public ResponseEntity<?> handleBusinessRule(RuntimeException e) {
        log.warn("[409] 비즈니스 규칙 위반: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ResponseDTO.fail(409, e.getMessage()));
    }

    // 422 — 잔액 부족 등 처리 불가
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<?> handleInsufficientBalance(InsufficientBalanceException e) {
        log.warn("[422] 처리 불가: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ResponseDTO.fail(422, e.getMessage()));
    }

    // 503 — AI 서비스 호출 실패
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<?> handleAiServiceUnavailable(AiServiceException e) {
        log.error("[503] AI 서비스 호출 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ResponseDTO.fail(503, "AI 서비스를 일시적으로 사용할 수 없습니다."));
    }

    // 500 — 서버 내부 오류
    @ExceptionHandler({IllegalStateException.class, Exception.class})
    public ResponseEntity<?> handleServerError(Exception e) {
        log.error("[500] 서버 내부 오류: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseDTO.fail(500, "서버 내부 오류가 발생했습니다."));
    }
}
