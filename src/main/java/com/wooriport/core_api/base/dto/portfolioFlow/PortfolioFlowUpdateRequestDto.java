package com.wooriport.core_api.base.dto.portfolioFlow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioFlowUpdateRequestDto {

    // 흐름 총 금액. null 이면 기존 값(AI 설정값) 유지
    private Long amount;

    // 모으기 통장. 보유 계좌 선택 시 asset_id, 추천 통장 유지 시 null (null 이면 기존 gathering 유지)
    private UUID gatheringAssetId;

    // 넣기 (상품)
    @Valid
    private List<ProductItem> products;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProductItem {
        // products 테이블 FK (옵션 — 시드/일부 경로에서 null 가능)
        private UUID productId;

        @NotNull
        @Min(0)
        private Integer productRatio;  // %

        // 가입된 자산(asset_id) — 옵션. 없으면 null
        private UUID assetId;
    }
}
