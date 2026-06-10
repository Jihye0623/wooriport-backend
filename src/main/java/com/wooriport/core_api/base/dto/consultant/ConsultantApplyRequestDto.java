package com.wooriport.core_api.base.dto.consultant;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultantApplyRequestDto {

    @NotBlank(message = "action을 입력해 주세요.")
    private String action;   // "salary" | "portfolio"

    private List<SalaryAllocation> salaryAllocations;
    private List<PortfolioItem> portfolio;
    private List<FlowUpdate> flows;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalaryAllocation {
        private String purpose;
        private int plannedAmount;
        private int ratio;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PortfolioItem {
        private String assetType;
        private int ratio;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FlowUpdate {
        private String flowId;
        private Long amount;
        private List<ProductItem> products;

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class ProductItem {
            private String productId;
            private int productRatio;
        }
    }
}
