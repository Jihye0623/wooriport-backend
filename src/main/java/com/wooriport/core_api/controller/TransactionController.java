package com.wooriport.core_api.controller;


import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.base.dto.transaction.SalaryTransactionListResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanListResponseDto;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.TransactionService;
import com.wooriport.core_api.service.TransferPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transactions", description = "거래 내역 API")
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "급여 입금 내역 조회",
            description = "sender_name에 '급여', '월급', '임금', 'salary' 포함된 입금 내역을 최신순으로 반환합니다."
    )
    @GetMapping("/salary")
    public ResponseEntity<ResponseDTO<SalaryTransactionListResponseDto>> getSalaryTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "급여 내역 조회 성공",
                transactionService.getSalaryTransactions(userDetails.getUserId())));
    }
}
