package com.wooriport.core_api.domain;

import lombok.Getter;

/**
 * 세제혜택 정책 상수 (2025년 기준 세법 고정값).
 * 한도/공제율은 세법으로 정해진 값이라 DB가 아닌 코드 상수로 관리한다.
 */
@Getter
public enum TaxBenefitPolicy {

    // benefitMaxContribution = 혜택 최대 납입액(세액공제 한도), annualMaxContribution = 1년 최대 납입한도
    PENSION_SAVINGS(6_000_000L, 18_000_000L, BenefitType.TAX_DEDUCTION),
    IRP(9_000_000L, 18_000_000L, BenefitType.TAX_DEDUCTION),   // 혜택 최대는 연금저축 합산 한도(900만)
    ISA(null, 20_000_000L, BenefitType.TAX_FREE);             // ISA는 세액공제 없음(비과세 혜택)

    /** 연금저축 단독 세액공제 한도 */
    public static final long PENSION_DEDUCTION_LIMIT = 6_000_000L;
    /** 연금저축 + IRP 합산 세액공제 한도 */
    public static final long PENSION_IRP_COMBINED_LIMIT = 9_000_000L;
    /** 세액공제율 기준 연 총급여 (이하 16.5%, 초과 13.2%) */
    public static final long INCOME_THRESHOLD = 55_000_000L;
    public static final double HIGH_RATE = 0.165;  // 총급여 5,500만원 이하
    public static final double LOW_RATE = 0.132;   // 총급여 5,500만원 초과

    private final Long benefitMaxContribution;  // ISA는 세액공제 한도가 없어 null
    private final long annualMaxContribution;
    private final BenefitType benefitType;

    TaxBenefitPolicy(Long benefitMaxContribution, long annualMaxContribution, BenefitType benefitType) {
        this.benefitMaxContribution = benefitMaxContribution;
        this.annualMaxContribution = annualMaxContribution;
        this.benefitType = benefitType;
    }

    public static TaxBenefitPolicy from(Assets.AccountType type) {
        return switch (type) {
            case PENSION_SAVINGS -> PENSION_SAVINGS;
            case IRP -> IRP;
            case ISA -> ISA;
            default -> throw new IllegalArgumentException("세제혜택 대상 계좌가 아닙니다: " + type);
        };
    }

    /** 연 총급여(원) 기준 세액공제율. salary 미입력 시 보수적으로 높은 공제율 적용 */
    public static double deductionRate(Long annualSalary) {
        if (annualSalary == null) return HIGH_RATE;
        return annualSalary <= INCOME_THRESHOLD ? HIGH_RATE : LOW_RATE;
    }

    @Getter
    public enum BenefitType {
        TAX_DEDUCTION("세액공제"),
        TAX_FREE("비과세");

        private final String label;

        BenefitType(String label) {
            this.label = label;
        }
    }
}
