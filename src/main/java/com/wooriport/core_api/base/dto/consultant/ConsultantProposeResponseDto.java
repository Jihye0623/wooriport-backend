package com.wooriport.core_api.base.dto.consultant;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ConsultantProposeResponseDto {

    private String summary;
    private String explanation;
    private List<SalaryAllocation> salaryAllocations;
    private List<PortfolioItem> portfolio;
    private List<FlowUpdate> flows;

    @Getter
    @Builder
    public static class SalaryAllocation {
        private String purpose;
        private int plannedAmount;
        private int ratio;
    }

    @Getter
    @Builder
    public static class PortfolioItem {
        private String assetType;
        private int ratio;
    }

    @Getter
    @Builder
    public static class FlowUpdate {
        private String flowId;
        private String flowTitle;
        private Long amount;
        private List<ProductItem> products;

        @Getter
        @Builder
        public static class ProductItem {
            private String productId;
            private String productName;
            private int productRatio;
        }
    }
}
