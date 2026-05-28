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

    // 모으기 통장 (Step 2). null 허용 — 비워둘 수도 있음
    private UUID gatheringAssetId;

    // 끌어오기 (PULL)
    @Valid
    private List<SourceItem> sources;

    // 넣기 (PUT)
    @Valid
    private List<ProductItem> products;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SourceItem {
        @NotNull
        private UUID assetId;

        @NotNull
        @Min(0)
        private Long amount;     // 원 단위
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProductItem {
        // products 테이블 FK (옵션 — 시드/일부 경로에서 null 가능)
        private UUID productId;

        // STOCK / BOND / DEPOSIT / SAVING / IRP
        @NotNull
        private String productType;

        @NotNull
        @Min(0)
        private Integer productRatio;  // %

        // 가입된 자산(asset_id) — 옵션. 없으면 null
        private UUID assetId;
    }
}
