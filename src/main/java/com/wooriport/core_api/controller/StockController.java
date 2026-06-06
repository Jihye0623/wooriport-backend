package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.base.dto.stock.StockDetailResponseDto;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.repository.ProductRepository;
import com.wooriport.core_api.service.YahooFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Stocks", description = "주식 시세 API")
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final YahooFinanceService yahooFinanceService;
    private final ProductRepository productRepository;

    @Operation(summary = "티커 현재가 및 30일 차트 조회. amount(원) 전달 시 살 수 있는 주 수 포함.")
    @GetMapping("/{ticker:.+}")
    public ResponseEntity<ResponseDTO<StockDetailResponseDto>> getStock(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String ticker,
            @RequestParam(required = false) Long amount) {

        StockDetailResponseDto data = yahooFinanceService.getStockDetail(ticker, amount);
        if (data == null) {
            return ResponseEntity.ok(ResponseDTO.fail(404, "시세 정보를 가져올 수 없습니다: " + ticker));
        }

        String koreanName = productRepository.findFirstByTicker(ticker)
                .map(p -> p.getName())
                .orElse(null);

        if (koreanName != null) {
            data = data.toBuilder().name(koreanName).build();
        }

        return ResponseEntity.ok(ResponseDTO.success(200, "주식 시세 조회 성공", data));
    }
}
