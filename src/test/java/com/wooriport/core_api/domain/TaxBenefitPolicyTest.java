package com.wooriport.core_api.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxBenefitPolicyTest {

    @Test
    @DisplayName("연 총급여가 정확히 5,500만원이면 고율(16.5%)이 적용된다")
    void deductionRate_exactlyAtThreshold_usesHighRate() {
        assertThat(TaxBenefitPolicy.deductionRate(55_000_000L))
                .isEqualTo(TaxBenefitPolicy.HIGH_RATE);
    }

    @Test
    @DisplayName("연 총급여가 5,500만원을 1원이라도 초과하면 저율(13.2%)이 적용된다")
    void deductionRate_justAboveThreshold_usesLowRate() {
        assertThat(TaxBenefitPolicy.deductionRate(55_000_001L))
                .isEqualTo(TaxBenefitPolicy.LOW_RATE);
    }

    @Test
    @DisplayName("급여 정보가 없으면 보수적으로 고율(16.5%)을 적용한다")
    void deductionRate_nullSalary_usesHighRate() {
        assertThat(TaxBenefitPolicy.deductionRate(null))
                .isEqualTo(TaxBenefitPolicy.HIGH_RATE);
    }

    @Test
    @DisplayName("계좌 타입별로 올바른 정책(한도/혜택유형)을 반환한다")
    void from_mapsAccountTypeToPolicy() {
        assertThat(TaxBenefitPolicy.from(Assets.AccountType.PENSION_SAVINGS))
                .isEqualTo(TaxBenefitPolicy.PENSION_SAVINGS);
        assertThat(TaxBenefitPolicy.from(Assets.AccountType.IRP))
                .isEqualTo(TaxBenefitPolicy.IRP);
        assertThat(TaxBenefitPolicy.from(Assets.AccountType.ISA))
                .isEqualTo(TaxBenefitPolicy.ISA);

        // ISA는 세액공제가 아닌 비과세 혜택이라 benefitMaxContribution(공제한도)이 없다
        assertThat(TaxBenefitPolicy.ISA.getBenefitMaxContribution()).isNull();
        assertThat(TaxBenefitPolicy.ISA.getBenefitType()).isEqualTo(TaxBenefitPolicy.BenefitType.TAX_FREE);
    }

    @Test
    @DisplayName("세제혜택 대상이 아닌 계좌 타입이면 예외를 던진다")
    void from_nonBenefitAccountType_throws() {
        assertThatThrownBy(() -> TaxBenefitPolicy.from(Assets.AccountType.CHECKING))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
