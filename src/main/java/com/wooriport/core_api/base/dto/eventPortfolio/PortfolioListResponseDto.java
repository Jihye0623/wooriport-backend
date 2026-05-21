package com.wooriport.core_api.base.dto.eventPortfolio;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioListResponseDto {

    private UUID eventId;
    private List<PortfolioItem> portfolios;
    private Integer totalRatio;       // 합계 (100이어야 정상)
    private Boolean isRebalanceable;  // 모든 계좌 연동 완료 시 true

    @Getter
    @Builder
    public static class PortfolioItem {
        private UUID id;
        private String productType;   // STOCK / BOND / DEPOSIT
        private Integer productRatio; // 비율
        private Boolean isLinked;     // 계좌 연동 여부
        private String institution;   // 연동된 기관명 (null이면 미연동)
        private String assetNumber;   // 계좌번호 (null이면 미연동)
        private Long balance;         // 현재 잔액 (null이면 미연동)
    }
}