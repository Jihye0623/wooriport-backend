package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.tax.TaxBenefitResponseDto;
import com.wooriport.core_api.base.dto.tax.TaxBenefitResponseDto.AccountBenefit;
import com.wooriport.core_api.base.dto.tax.TaxBenefitResponseDto.PensionSummary;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Assets.AccountType;
import com.wooriport.core_api.domain.TaxBenefitAccounts;
import com.wooriport.core_api.domain.TaxBenefitPolicy;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TaxBenefitAccountRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaxBenefitService {

    private final AssetRepository assetRepository;
    private final TaxBenefitAccountRepository taxBenefitAccountRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TaxBenefitResponseDto getTaxBenefits(UUID userId) {
        Users user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        List<Assets> accounts = assetRepository.findTaxBenefitAccounts(userId);

        // 세액공제율: 연 총급여(월급 * 12) 기준
        Long salary = user.getSalary();
        double rate = TaxBenefitPolicy.deductionRate(salary == null ? null : salary * 12);

        // ISA 납입원금 로드 (수익률 계산용)
        Map<UUID, Long> principalByAsset = loadPrincipals(accounts);

        // 연금저축 / IRP 납입액 합 (계좌 여러 개여도 타입별 합산)
        long pensionContribution = sumBalance(accounts, AccountType.PENSION_SAVINGS);
        long irpContribution = sumBalance(accounts, AccountType.IRP);

        // 합산 900만 한도: 연금저축(최대 600)을 먼저 채우고 남은 한도를 IRP에 배분
        long pensionDeductible = Math.min(pensionContribution, TaxBenefitPolicy.PENSION_DEDUCTION_LIMIT);
        long irpDeductible = Math.max(0,
                Math.min(irpContribution, TaxBenefitPolicy.PENSION_IRP_COMBINED_LIMIT - pensionDeductible));

        List<AccountBenefit> items = accounts.stream()
                .map(a -> toAccountBenefit(a, principalByAsset, rate,
                        pensionContribution, pensionDeductible,
                        irpContribution, irpDeductible))
                .collect(Collectors.toList());

        long deductibleAmount = pensionDeductible + irpDeductible;
        PensionSummary summary = PensionSummary.builder()
                .totalContribution(pensionContribution + irpContribution)
                .combinedBenefitMaxContribution(TaxBenefitPolicy.PENSION_IRP_COMBINED_LIMIT)
                .deductibleAmount(deductibleAmount)
                .deductionRate(rate * 100)
                .totalTaxDeduction(Math.round(deductibleAmount * rate))
                .build();

        return TaxBenefitResponseDto.builder()
                .accounts(items)
                .pensionSummary(summary)
                .build();
    }

    private AccountBenefit toAccountBenefit(Assets a, Map<UUID, Long> principalByAsset, double rate,
                                            long pensionContribution, long pensionDeductible,
                                            long irpContribution, long irpDeductible) {
        TaxBenefitPolicy policy = TaxBenefitPolicy.from(a.getAssetType());

        AccountBenefit.AccountBenefitBuilder builder = AccountBenefit.builder()
                .assetId(a.getId())
                .accountType(a.getAssetType().name())
                .institution(a.getInstitution())
                .accountName(a.getAccountName())
                .benefitType(policy.getBenefitType().getLabel())
                .currentContribution(a.getBalance())
                .benefitMaxContribution(policy.getBenefitMaxContribution())
                .annualMaxContribution(policy.getAnnualMaxContribution());

        switch (a.getAssetType()) {
            case ISA -> {
                Long principal = principalByAsset.get(a.getId());
                if (principal != null && principal > 0) {
                    long profit = a.getBalance() - principal;
                    builder.principal(principal)
                            .profit(profit)
                            .returnRate(round2(profit * 100.0 / principal));
                }
            }
            // 합산 한도로 산출한 공제 대상을 계좌별 납입 비중만큼 배분 → 카드 합 = 합산 공제액
            case PENSION_SAVINGS -> builder.taxDeduction(
                    deductionShare(a.getBalance(), pensionContribution, pensionDeductible, rate));
            case IRP -> builder.taxDeduction(
                    deductionShare(a.getBalance(), irpContribution, irpDeductible, rate));
            default -> { /* 세제혜택 대상 외 계좌는 조회되지 않음 */ }
        }

        return builder.build();
    }

    private Map<UUID, Long> loadPrincipals(List<Assets> accounts) {
        if (accounts.isEmpty()) return Map.of();
        List<UUID> ids = accounts.stream().map(Assets::getId).collect(Collectors.toList());
        return taxBenefitAccountRepository.findByAssetIdIn(ids).stream()
                .filter(t -> t.getPrincipal() != null)
                .collect(Collectors.toMap(t -> t.getAsset().getId(), TaxBenefitAccounts::getPrincipal));
    }

    private long sumBalance(List<Assets> accounts, AccountType type) {
        return accounts.stream()
                .filter(a -> a.getAssetType() == type)
                .mapToLong(Assets::getBalance)
                .sum();
    }

    /** 타입 합산 공제대상(typeDeductible)을 계좌 납입 비중만큼 배분한 세액공제액 */
    private long deductionShare(long balance, long typeContribution, long typeDeductible, double rate) {
        if (typeContribution <= 0) return 0;
        double share = (double) typeDeductible * balance / typeContribution;
        return Math.round(share * rate);
    }

    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
