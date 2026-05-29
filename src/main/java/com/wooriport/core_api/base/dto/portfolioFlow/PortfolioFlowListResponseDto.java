package com.wooriport.core_api.base.dto.portfolioFlow;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioFlowListResponseDto {

    private Long monthlyInvestAmount;   // users.monthly_invest_amount — 월 총 투자액
    private List<FlowDto> flows;

    @Getter
    @Builder
    public static class FlowDto {
        private UUID id;
        private UUID eventId;          // null = 기본 흐름
        private String title;
        private String summary;
        private String term;           // "단" / "중" / "장"
        private Long amount;           // 모을 통장 월 납입 금액
        private Boolean isActive;

        private GatheringAssetDto gatheringAsset;   // step2 (모으기 / 허브)
        private List<SourceItemDto> sources;        // step1 (끌어오기 / PULL)
        private List<ProductItemDto> products;      // step3 (넣기 / PUT)
    }

    @Getter
    @Builder
    public static class GatheringAssetDto {
        private UUID id;
        private String institution;
        private String accountName;
        private String assetNumber;
        private String assetType;     // CHECKING / IRP / ISA ... (프론트가 kind 파생)
        private Long balance;
    }

    @Getter
    @Builder
    public static class SourceItemDto {
        private UUID id;              // portfolio_flow_items.id
        private Long amount;          // 끌어올 금액

        private UUID assetId;
        private String institution;
        private String accountName;
        private String assetNumber;
        private String assetType;
    }

    @Getter
    @Builder
    public static class ProductItemDto {
        private UUID id;              // portfolio_flow_items.id
        private Integer productRatio; // %
        private String productType;   // STOCK / BOND / DEPOSIT / SAVING / IRP

        private UUID productId;
        private String productName;
        private String productInstitution;
        private Float interestRate;   // products.interest_rate → 프론트 rate 표시용
    }
}
