package com.wooriport.core_api.base.dto.asset;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetSummaryResponseDto {
    private Long totalBalance;   // 전체 자산 합산
    private Long savingsBalance;    // 예적금 총자산
    private Long investBalance;     // 투자 총자산
    private int linkedAccountCount; // 연결된 계좌 수
    private int linkedCardCount;    // 연결된 카드 수
}
