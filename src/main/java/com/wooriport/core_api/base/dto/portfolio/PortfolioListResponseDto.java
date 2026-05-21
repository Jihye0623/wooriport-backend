package com.wooriport.core_api.base.dto.portfolio;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioListResponseDto {

    private List<PortfolioItem> portfolios;
    private int totalRatio;       // 비율 합계 (정상 = 100)
    private Long salaryAmount;    // 기준 급여 (최근 트랜잭션)

    @Getter
    @Builder
    public static class PortfolioItem {
        private UUID id;
        private String assetType;    // STOCK / BOND / CASH / IRP / EMERGENCY / FIXED
        private Integer assetRatio;  // 비율 (%)
        private Long amount;         // 급여 × 비율 (salaryAmount 없으면 null)
        private Boolean isLinked;    // 계좌 연동 여부
        private String institution;  // 연동 기관명
        private String assetNumber;  // 연동 계좌번호
        private Long balance;        // 현재 잔액
    }
}