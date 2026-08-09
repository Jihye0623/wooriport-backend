package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.dashboard.DashboardResponseDto;
import com.wooriport.core_api.base.dto.tax.TaxBenefitResponseDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.TaxBenefitAccounts;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TaxBenefitAccountRepository;
import com.wooriport.core_api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TaxBenefitServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock TaxBenefitAccountRepository taxBenefitAccountRepository;
    @Mock UserRepository userRepository;
    @InjectMocks TaxBenefitService taxBenefitService;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("존재하지 않는 유저면 UserNotFoundException")
    void getTaxBenefits_userNotFound_throws() {
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> taxBenefitService.getTaxBenefits(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("연금저축만 단독 한도(600만)를 초과하면 초과분은 공제 대상에서 제외된다")
    void getTaxBenefits_pensionOnlyExceedsLimit() {
        Users user = user(60_000_000L); // 연 총급여 낮게 잡아 고율 적용 대상
        Assets pension = pensionAsset(8_000_000L); // 600만 한도 초과
        mockAccounts(user, List.of(pension));

        TaxBenefitResponseDto result = taxBenefitService.getTaxBenefits(userId);

        assertThat(result.getPensionSummary().getDeductibleAmount()).isEqualTo(6_000_000L);
        assertThat(result.getPensionSummary().getTotalContribution()).isEqualTo(8_000_000L);
    }

    @Test
    @DisplayName("IRP만 있을 때는 합산 한도(900만)까지 전액 공제 대상이 된다")
    void getTaxBenefits_irpOnlyWithinCombinedLimit() {
        Users user = user(60_000_000L);
        Assets irp = irpAsset(12_000_000L); // 900만 한도 초과 납입
        mockAccounts(user, List.of(irp));

        TaxBenefitResponseDto result = taxBenefitService.getTaxBenefits(userId);

        assertThat(result.getPensionSummary().getDeductibleAmount()).isEqualTo(9_000_000L);
    }

    @Test
    @DisplayName("둘 다 한도 이내면 납입액 그대로 합산 공제된다")
    void getTaxBenefits_bothWithinLimit() {
        Users user = user(60_000_000L);
        Assets pension = pensionAsset(3_000_000L);
        Assets irp = irpAsset(2_000_000L);
        mockAccounts(user, List.of(pension, irp));

        TaxBenefitResponseDto result = taxBenefitService.getTaxBenefits(userId);

        assertThat(result.getPensionSummary().getDeductibleAmount()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("연금저축(600만 한도 소진) 초과분을 IRP가 합산 한도(900만)까지 채운다")
    void getTaxBenefits_bothExceedCombinedLimit_capsAtNineMillion() {
        Users user = user(60_000_000L);
        Assets pension = pensionAsset(7_000_000L); // 단독 한도(600만) 초과 -> 600만만 인정
        Assets irp = irpAsset(5_000_000L);         // 남은 한도(900-600=300만)까지만 인정
        mockAccounts(user, List.of(pension, irp));

        TaxBenefitResponseDto result = taxBenefitService.getTaxBenefits(userId);

        assertThat(result.getPensionSummary().getDeductibleAmount()).isEqualTo(9_000_000L);
    }

    @Test
    @DisplayName("납입액이 0원이면 공제 대상도 0원이다")
    void getTaxBenefits_zeroContribution() {
        Users user = user(60_000_000L);
        Assets pension = pensionAsset(0L);
        mockAccounts(user, List.of(pension));

        TaxBenefitResponseDto result = taxBenefitService.getTaxBenefits(userId);

        assertThat(result.getPensionSummary().getDeductibleAmount()).isZero();
    }

    @Test
    @DisplayName("ISA 계좌는 납입원금이 등록되어 있으면 수익률을 계산한다")
    void getTaxBenefits_isaWithPrincipal_calculatesReturnRate() {
        Users user = user(60_000_000L);
        Assets isa = isaAsset(6_000_000L); // 잔액 600만
        mockAccounts(user, List.of(isa));
        given(taxBenefitAccountRepository.findByAssetIdIn(any()))
                .willReturn(List.of(TaxBenefitAccounts.builder().asset(isa).principal(5_000_000L).build()));

        TaxBenefitResponseDto result = taxBenefitService.getTaxBenefits(userId);

        TaxBenefitResponseDto.AccountBenefit isaBenefit = result.getAccounts().get(0);
        assertThat(isaBenefit.getPrincipal()).isEqualTo(5_000_000L);
        assertThat(isaBenefit.getProfit()).isEqualTo(1_000_000L);
        assertThat(isaBenefit.getReturnRate()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("ISA 계좌에 납입원금이 등록되어 있지 않으면 수익률 필드는 비어있다")
    void getTaxBenefits_isaWithoutPrincipal_leavesReturnFieldsNull() {
        Users user = user(60_000_000L);
        Assets isa = isaAsset(6_000_000L);
        mockAccounts(user, List.of(isa));
        given(taxBenefitAccountRepository.findByAssetIdIn(any())).willReturn(List.of());

        TaxBenefitResponseDto result = taxBenefitService.getTaxBenefits(userId);

        TaxBenefitResponseDto.AccountBenefit isaBenefit = result.getAccounts().get(0);
        assertThat(isaBenefit.getPrincipal()).isNull();
        assertThat(isaBenefit.getProfit()).isNull();
        assertThat(isaBenefit.getReturnRate()).isNull();
    }

    @Test
    @DisplayName("대시보드 위젯: 합산 한도를 이미 초과했으면 남은 여력(remaining)은 음수가 아닌 0이다")
    void getDashboardTaxSaving_remainingNeverNegative() {
        Users user = user(60_000_000L);
        Assets pension = pensionAsset(6_000_000L);
        Assets irp = irpAsset(6_000_000L); // 합산 1200만 납입, 한도(900만) 초과
        mockAccounts(user, List.of(pension, irp));

        DashboardResponseDto.TaxSaving result = taxBenefitService.getDashboardTaxSaving(userId);

        assertThat(result.getRemaining()).isZero();
    }

    private void mockAccounts(Users user, List<Assets> accounts) {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(assetRepository.findTaxBenefitAccounts(userId)).willReturn(accounts);
        lenient().when(taxBenefitAccountRepository.findByAssetIdIn(any())).thenReturn(List.of());
    }

    private Users user(Long monthlySalary) {
        return Users.builder().id(userId).name("홍길동").salary(monthlySalary / 12).build();
    }

    private Assets pensionAsset(Long balance) {
        return asset(Assets.AccountType.PENSION_SAVINGS, balance);
    }

    private Assets irpAsset(Long balance) {
        return asset(Assets.AccountType.IRP, balance);
    }

    private Assets isaAsset(Long balance) {
        return asset(Assets.AccountType.ISA, balance);
    }

    private Assets asset(Assets.AccountType type, Long balance) {
        return Assets.builder()
                .id(UUID.randomUUID())
                .institution("우리은행")
                .assetType(type)
                .balance(balance)
                .bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now())
                .build();
    }
}
