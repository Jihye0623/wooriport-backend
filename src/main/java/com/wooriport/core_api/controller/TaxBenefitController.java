package com.wooriport.core_api.controller;

import com.wooriport.core_api.base.dto.response.ResponseDTO;
import com.wooriport.core_api.base.dto.tax.TaxBenefitResponseDto;
import com.wooriport.core_api.config.security.CustomUserDetails;
import com.wooriport.core_api.service.TaxBenefitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "TaxBenefit", description = "세제혜택(ISA / IRP / 연금저축펀드) 조회 API")
@RestController
@RequestMapping("/api/v1/tax-benefits")
@RequiredArgsConstructor
public class TaxBenefitController {

    private final TaxBenefitService taxBenefitService;

    @Operation(
            summary = "세제혜택 현황 조회",
            description = "ISA / IRP / 연금저축펀드 계좌별 납입액·한도와 혜택 현황(ISA 수익률, IRP·연금저축 세액공제)을 반환합니다."
    )
    @GetMapping
    public ResponseEntity<ResponseDTO<TaxBenefitResponseDto>> getTaxBenefits(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ResponseDTO.success(200, "세제혜택 조회 성공",
                taxBenefitService.getTaxBenefits(userDetails.getUserId())));
    }
}
