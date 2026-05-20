package com.wooriport.core_api.base.dto.eventPortfolio;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.util.UUID;

@Getter
public class PortfolioUpdateRequestDto {

    @NotNull private Integer stockRatio;    // 주식 비율
    @NotNull private Integer bondRatio;     // 채권 비율
    @NotNull private Integer cashRatio;     // 현금/예금 비율

    // 계좌 변경 시 (null이면 기존 유지)
    private UUID stockAssetId;
    private UUID bondAssetId;
    private UUID depositAssetId;
}