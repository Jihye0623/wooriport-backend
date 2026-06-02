package com.wooriport.core_api.base.dto.tax;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class TaxBenefitResponseDto {

    private List<AccountBenefit> accounts;
    private PensionSummary pensionSummary;   // 연금저축 + IRP 합산 세액공제 현황

    @Getter
    @Builder
    public static class AccountBenefit {
        private UUID assetId;
        private String accountType;             // ISA / IRP / PENSION_SAVINGS
        private String institution;
        private String accountName;
        private String benefitType;             // 세액공제 / 비과세

        private Long currentContribution;       // 1년간 현재 납입액 (= 잔액)
        private Long benefitMaxContribution;    // 혜택 최대 납입액(세액공제 한도), ISA는 null
        private Long annualMaxContribution;     // 1년 최대 납입한도

        // ISA 전용 혜택 현황 (납입원금 미등록 시 null)
        private Long principal;                 // 납입원금
        private Long profit;                    // 수익금 (잔액 - 납입원금)
        private Double returnRate;              // 수익률(%)

        // IRP / 연금저축 전용 혜택 현황
        private Long taxDeduction;              // 예상 세액공제액 (합산 한도 반영)
    }

    @Getter
    @Builder
    public static class PensionSummary {
        private Long totalContribution;             // 연금저축 + IRP 납입액 합
        private Long combinedBenefitMaxContribution;// 합산 세액공제 한도 (900만)
        private Long deductibleAmount;              // 실제 공제 대상 납입액 (한도 반영)
        private Double deductionRate;              // 적용 공제율(%) 16.5 / 13.2
        private Long totalTaxDeduction;            // 합산 예상 세액공제액
    }
}
