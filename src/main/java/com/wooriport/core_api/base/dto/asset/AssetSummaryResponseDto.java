package com.wooriport.core_api.base.dto.asset;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetSummaryResponseDto {
    private Long totalBalance;   // 전체 자산 합산
    private int assetCount;      // 카드 잔액 합산
}
