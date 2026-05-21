package com.wooriport.core_api.base.dto.event;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class PurchaseResponseDto {
    private String itemName;
    private List<Candidate> candidates;

    @Getter
    @Builder
    public static class Candidate {
        private String productName;      // 상품명
        private Long estimatedPrice;     // 예상 가격
        private String description;      // 설명
    }
}

