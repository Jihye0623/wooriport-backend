package com.wooriport.core_api.base.dto.portfolioFlow;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AvailableAssetListResponseDto {

    private List<AssetDto> assets;

    @Getter
    @Builder
    public static class AssetDto {
        private UUID id;
        private String institution;
        private String accountName;
        private String assetNumber;
        private String assetType;   // CHECKING / PARKING / SAVINGS / DEPOSIT / STOCK / CMA / IRP / ISA
        private Long balance;
    }
}
