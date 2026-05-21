package com.wooriport.core_api.base.dto.asset;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class MydataPreviewResponseDto {

    private List<MydataItem> accounts;
    private int totalCount;

    @Getter
    @Builder
    public static class MydataItem {
        private String assetNumber;    // 계좌번호 (선택 기준)
        private String institution;    // 기관명
        private String assetType;      // CHECKING / SAVINGS / CREDIT_CARD 등
        private String accountName;    // 상품명
        private String accountPurpose; // 별명
        private Long balance;          // 잔액
        private String bankType;       // WOORI / OTHER
        private Boolean isSalary;      // 급여통장 여부
    }
}