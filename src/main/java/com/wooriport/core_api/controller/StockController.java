package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.base.dto.stock.StockDetailResponseDto;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.repository.MiniChallengesRepository;
import com.wooriport.core_api.repository.ProductRepository;
import com.wooriport.core_api.service.YahooFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Stocks", description = "주식 시세 API")
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final YahooFinanceService yahooFinanceService;
    private final ProductRepository productRepository;
    private final MiniChallengesRepository miniChallengesRepository;

    @Operation(summary = "미니챌린지의 보상 주식 시세 및 살 수 있는 주 수 조회",
            description = "challengeId를 주면 해당 챌린지(상태 무관)의 보상 주식을, 없으면 진행 중인 챌린지를 조회합니다.")
    @GetMapping
    public ResponseEntity<ResponseDTO<StockDetailResponseDto>> getStock(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) UUID challengeId) {

        // challengeId가 오면 그 챌린지(성공/실패 포함)를, 없으면 진행 중인 챌린지를 사용
        MiniChallenges challenge = (challengeId != null
                ? miniChallengesRepository.findByIdAndUserId(challengeId, userDetails.getUserId())
                : miniChallengesRepository.findFirstByUserIdAndStatus(userDetails.getUserId(), MiniChallenges.ChallengeStatus.IN_PROGRESS))
                .orElse(null);

        if (challenge == null || challenge.getRewardStockTicker() == null) {
            return ResponseEntity.ok(ResponseDTO.fail(404, "진행 중인 챌린지가 없습니다"));
        }

        String ticker = challenge.getRewardStockTicker();
        Long amount = challenge.getEstimatedSaving();

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
