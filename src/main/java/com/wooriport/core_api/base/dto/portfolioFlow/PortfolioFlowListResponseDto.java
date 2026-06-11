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
        private String title;
        private String summary;
        private String term;           // 단기 / 중기 / 장기 등 (AI 원본)
        private Long amount;           // 모을 통장 월 납입 금액
        private Boolean isActive;

        // true = 계좌 추천(gatheringAsset=null, 추천 계좌는 products 로 내려감)
        // false = 보유 계좌 모으기(gatheringAsset 존재)
        private Boolean isRecommendation;
        private String reasoning;          // 흐름 배분 근거 (AI)

        private Double expectedRrPct;      // 1년 예상 수익률(%)
        private Integer investmentMonths;  // 예상 산정 기간(개월)
        private Double expectedAmount;     // 복리 고려 N개월 후 예상 수익
        private String rrComment;          // 수익률 코멘트 (AI)

        private GatheringAssetDto gatheringAsset;   // step2 (모으기 / 허브)
        private List<SourceItemDto> sources;        // step1 (끌어오기 / PULL) — 현재 미사용, 빈 배열
        private List<ProductItemDto> products;      // step3 (넣기 / PUT)
    }

    // 보유 계좌 선택 / 계좌 추천 둘 다 채워짐. 추천이면 id·assetNumber·balance 는 null, interestRate 표시
    @Getter
    @Builder
    public static class GatheringAssetDto {
        private UUID id;              // 보유 계좌면 asset_id, 추천이면 null
        private String institution;
        private String accountName;
        private String assetNumber;   // 추천이면 null
        private String assetType;     // CHECKING / IRP / ISA ... (프론트가 kind 파생)
        private Long balance;         // 추천이면 null
        private Double interestRate;  // 추천 통장 금리(예/적금일 때)
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
        private String productType;      // SAVING(적금) / DEPOSIT(예금) / STOCK(주식) / BOND(채권) / IRP / ETF / PENSION_SAVINGS / ISA

        private UUID productId;
        private String productName;
        private String productInstitution;
        private Float interestRate;   // products.interest_rate → 프론트 rate 표시용
    }
}
