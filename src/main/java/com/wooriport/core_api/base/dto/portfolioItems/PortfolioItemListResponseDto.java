package com.wooriport.core_api.base.dto.portfolioItems;


import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioItemListResponseDto {

    private List<ItemDto> items;
    private int totalRatio;    // 비율 합계 (정상 = 100)
    private UUID eventId;      // null = 기본 포트폴리오 / 있으면 이벤트 포트폴리오

    @Getter
    @Builder
    public static class ItemDto {
        private UUID id;
        private String productType;   // STOCK / BOND / DEPOSIT
        private Integer productRatio; // 비율 (%)
        private Boolean isLinked;     // 계좌 연동 여부
        private String institution;   // 연동 기관명
        private String assetNumber;   // 계좌번호
        private Long balance;         // 현재 잔액
    }
}