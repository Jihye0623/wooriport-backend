package com.wooriport.core_api.base.dto.transaction;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SalaryTransactionListResponseDto {

    private List<SalaryItem> salaryTransactions;
    private int totalCount;

    @Getter
    @Builder
    public static class SalaryItem {
        private UUID id;
        private UUID assetId;
        private String institution;   // 입금된 계좌 기관명
        private Long amount;          // 입금액 (양수)
        private String senderName;    // 입금자명 ("(주)우리회사 급여")
        private String transactionAt; // 거래 시각
    }
}
