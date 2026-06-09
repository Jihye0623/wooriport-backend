package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.dashboard.DashboardResponseDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.PortfolioFlowItems;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.ProductCategoryRate;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioFlowItemRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
import com.wooriport.core_api.repository.ProductCategoryRateRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioFlowItemRepository portfolioFlowItemRepository;
    private final TransactionRepository transactionRepository;
    private final ProductCategoryRateRepository productCategoryRateRepository;
    private final TaxBenefitService taxBenefitService;

    // assets.assetType → 현금성 / 투자자산 분류
    private static final Set<Assets.AccountType> CASH_TYPES = Set.of(
            Assets.AccountType.CHECKING,
            Assets.AccountType.PARKING,
            Assets.AccountType.SAVINGS,
            Assets.AccountType.DEPOSIT,
            Assets.AccountType.CMA,
            Assets.AccountType.HOUSING_SUBSCRIPTION);
    private static final Set<Assets.AccountType> INVESTMENT_TYPES = Set.of(
            Assets.AccountType.STOCK,
            Assets.AccountType.IRP,
            Assets.AccountType.ISA,
            Assets.AccountType.PENSION_SAVINGS);

    @Transactional(readOnly = true)
    public DashboardResponseDto getDashboard(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);
        List<Portfolios> portfolios = portfolioRepository.findByUserId(userId);
        List<PortfolioFlowItems> flowPutItems = portfolioFlowItemRepository.findAllPutByUserIdWithAsset(userId);

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        List<Object[]> categoryRows = transactionRepository.sumExpenseGroupByCategory(userId, year, month);
        List<Transactions> monthlyExpenses = transactionRepository.findMonthlyExpenses(userId, year, month);

        // 예산 기준선 = 저번 달 소비총합 (transactions 기반)
        LocalDate lastMonth = today.minusMonths(1);
        long lastMonthExpense = transactionRepository.sumExpenseByMonth(
                userId, lastMonth.getYear(), lastMonth.getMonthValue());

        // 이번 주 요일별 지출 [월~금] — 이번 주 월요일 00:00 ~ 토요일 00:00 거래를 요일로 합산
        LocalDate weekMonday = today.minusDays(today.getDayOfWeek().getValue() - 1);
        List<Long> weeklyExpenses = buildWeeklyExpenses(userId, weekMonday);

        Map<String, String> rateByLabel = productCategoryRateRepository.findAll().stream()
                .collect(Collectors.toMap(
                        ProductCategoryRate::getCategoryLabel,
                        r -> r.getRate() == null ? "-" : r.getRate(),
                        (a, b) -> a));

        return DashboardResponseDto.builder()
                .user(buildUser(user))
                .assetsSummary(buildAssetsSummary(assets))
                .salaryPlan(buildSalaryPlan(user, portfolios))
                .consumption(buildConsumption(month, categoryRows, monthlyExpenses, lastMonthExpense, weeklyExpenses))
                .portfolio(buildPortfolio(flowPutItems, rateByLabel))
                .taxSaving(taxBenefitService.getDashboardTaxSaving(userId))
                .build();
    }

    private DashboardResponseDto.UserInfo buildUser(Users user) {
        return DashboardResponseDto.UserInfo.builder()
                .id(user.getId())
                .name(user.getName())
                .build();
    }

    // assets.assetType 기준 분류
    private DashboardResponseDto.AssetsSummary buildAssetsSummary(List<Assets> assets) {
        long total = 0L;
        long cash = 0L;
        long invest = 0L;
        for (Assets a : assets) {
            long b = a.getBalance() == null ? 0L : a.getBalance();
            Assets.AccountType t = a.getAssetType();
            if (t == null) continue;
            if (CASH_TYPES.contains(t)) {
                cash += b;
                total += b;
            } else if (INVESTMENT_TYPES.contains(t)) {
                invest += b;
                total += b;
            }
        }
        return DashboardResponseDto.AssetsSummary.builder()
                .totalBalance(total)
                .investmentBalance(invest)
                .cashBalance(cash)
                .build();
    }

    private DashboardResponseDto.SalaryPlan buildSalaryPlan(Users user, List<Portfolios> portfolios) {
        long monthlyIncome     = user.getSalary() == null ? 0L : user.getSalary();
        long investmentAmount  = user.getMonthlyInvestAmount() == null ? 0L : user.getMonthlyInvestAmount();

        List<DashboardResponseDto.Allocation> allocations = portfolios.stream()
                .map(p -> DashboardResponseDto.Allocation.builder()
                        .purpose(p.getAsset() != null ? p.getAsset().getAccountPurpose() : null)
                        .plannedAmount(p.getAssetAmount())
                        .build())
                .toList();

        long allocatedSum = allocations.stream()
                .mapToLong(a -> a.getPlannedAmount() == null ? 0L : a.getPlannedAmount())
                .sum();
        long surplus = monthlyIncome - allocatedSum - investmentAmount;

        return DashboardResponseDto.SalaryPlan.builder()
                .monthlyIncome(monthlyIncome)
                .investmentAmount(investmentAmount)
                .surplus(surplus)
                .allocations(allocations)
                .build();
    }

    // 이번 주(월~일) 요일별 지출 합계. 인덱스 0=월 … 6=일
    private List<Long> buildWeeklyExpenses(UUID userId, LocalDate weekMonday) {
        LocalDateTime start = weekMonday.atStartOfDay();
        LocalDateTime end = weekMonday.plusDays(7).atStartOfDay().minusNanos(1);   // 일요일 끝 (findExpensesBetween 은 <= to)
        long[] weekly = new long[7];
        for (Transactions t : transactionRepository.findExpensesBetween(userId, start, end)) {
            if (t.getAmount() == null || t.getTransactionAt() == null) continue;
            int dow = t.getTransactionAt().getDayOfWeek().getValue();   // 월=1 … 일=7
            weekly[dow - 1] += Math.abs(t.getAmount());
        }
        List<Long> result = new ArrayList<>(7);
        for (long v : weekly) result.add(v);
        return result;
    }

    private DashboardResponseDto.Consumption buildConsumption(
            int month,
            List<Object[]> categoryRows,
            List<Transactions> monthlyExpenses,
            long lastMonthExpense,
            List<Long> weeklyExpenses) {

        long totalExpense = categoryRows.stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();

        // 예산 기준 = 저번 달 소비총합. 저번 달 데이터가 없으면 초과 판정하지 않음.
        boolean isBudgetExceeded = lastMonthExpense > 0 && totalExpense > lastMonthExpense;
        int budgetExceedRate = isBudgetExceeded
                ? (int) Math.round(((totalExpense - lastMonthExpense) * 100.0) / lastMonthExpense)
                : 0;

        // 카테고리별 [총 거래건수, top 가맹점]
        Map<String, List<Transactions>> byCategory = monthlyExpenses.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(Transactions::getCategory));

        List<DashboardResponseDto.CategoryExpense> categories = categoryRows.stream()
                .map(row -> {
                    String name = (String) row[0];
                    long amount = ((Number) row[1]).longValue();
                    int percentage = totalExpense > 0
                            ? (int) Math.round((amount * 100.0) / totalExpense)
                            : 0;
                    return DashboardResponseDto.CategoryExpense.builder()
                            .categoryName(name)
                            .expenseAmount(amount)
                            .percentage(percentage)
                            .sub(buildCategorySub(byCategory.get(name)))
                            .build();
                })
                .toList();

        return DashboardResponseDto.Consumption.builder()
                .referenceMonth(month)
                .totalExpense(totalExpense)
                .lastMonthExpense(lastMonthExpense)
                .isBudgetExceeded(isBudgetExceeded)
                .budgetExceedRate(budgetExceedRate)
                .weeklyExpenses(weeklyExpenses)
                .categories(categories)
                .build();
    }

    // "배달의민족 외 24건" 형식
    private String buildCategorySub(List<Transactions> txs) {
        if (txs == null || txs.isEmpty()) return null;
        Map<String, Long> byMerchant = txs.stream()
                .filter(t -> t.getSenderName() != null && !t.getSenderName().isBlank())
                .collect(Collectors.groupingBy(Transactions::getSenderName, Collectors.counting()));
        if (byMerchant.isEmpty()) return txs.size() + "건";
        String top = byMerchant.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        int others = txs.size() - 1;
        return others > 0 ? top + " 외 " + others + "건" : top;
    }

    // PortfolioFlowItems(PUT) 의 productType → 화면 라벨 ETF/현금성/적금/IRP
    private String labelOf(String productType) {
        if (productType == null) return null;
        return switch (productType.toUpperCase()) {
            case "STOCK", "BOND" -> "ETF";
            case "DEPOSIT"       -> "현금성";
            case "SAVING"        -> "적금";
            case "IRP"           -> "IRP";
            default              -> null;
        };
    }

    private List<DashboardResponseDto.PortfolioItem> buildPortfolio(
            List<PortfolioFlowItems> putItems,
            Map<String, String> rateByLabel) {

        // label → [(name, amount)] 누적
        //   금액 산정: PUT item 에 asset 이 직접 묶여 있으면 그 자산 잔액,
        //              없으면 (prescription 으로 생성된 경우) 흐름의 모으기 통장 잔액을 사용.
        //   amount = sourceAsset.balance × productRatio / 100
        Map<String, List<NamedAmount>> bucketsByLabel = new LinkedHashMap<>();
        for (PortfolioFlowItems pi : putItems) {
            String productType = (pi.getProduct() != null && pi.getProduct().getProductType() != null)
                    ? pi.getProduct().getProductType().name()
                    : null;
            String label = labelOf(productType);
            if (label == null) continue;

            Assets sourceAsset = pi.getAsset();
            if (sourceAsset == null && pi.getFlow() != null) {
                sourceAsset = pi.getFlow().getGatheringAsset();
            }
            long balance = (sourceAsset != null && sourceAsset.getBalance() != null)
                    ? sourceAsset.getBalance()
                    : 0L;

            int ratio = pi.getProductRatio() == null ? 0 : pi.getProductRatio();
            long amount = balance * ratio / 100;
            bucketsByLabel.computeIfAbsent(label, k -> new ArrayList<>())
                          .add(new NamedAmount(resolveName(pi), amount));
        }

        long total = bucketsByLabel.values().stream()
                .flatMap(List::stream)
                .mapToLong(NamedAmount::amount)
                .sum();

        return bucketsByLabel.entrySet().stream()
                .map(e -> {
                    String label = e.getKey();
                    String rate = rateByLabel.getOrDefault(label, "-");
                    long catSum = e.getValue().stream().mapToLong(NamedAmount::amount).sum();

                    List<DashboardResponseDto.PortfolioSubItem> items = e.getValue().stream()
                            .map(na -> DashboardResponseDto.PortfolioSubItem.builder()
                                    .name(na.name())
                                    .ratio(total > 0 ? (int) Math.round(na.amount() * 100.0 / total) : 0)
                                    .rate(rate)
                                    .build())
                            .toList();

                    return DashboardResponseDto.PortfolioItem.builder()
                            .categoryLabel(label)
                            .assetAmount(catSum)
                            .ratio(total > 0 ? (int) Math.round(catSum * 100.0 / total) : 0)
                            .rate(rate)
                            .items(items)
                            .build();
                })
                .toList();
    }

    // 상품 이름: products.name 우선, 없으면 assets.accountName, 그것도 없으면 institution
    private String resolveName(PortfolioFlowItems pi) {
        if (pi.getProduct() != null && pi.getProduct().getName() != null) {
            return pi.getProduct().getName();
        }
        if (pi.getAsset() != null) {
            Assets a = pi.getAsset();
            if (a.getAccountName() != null && !a.getAccountName().isBlank()) {
                return a.getAccountName();
            }
            if (a.getInstitution() != null) return a.getInstitution();
        }
        return "(이름 없음)";
    }

    private record NamedAmount(String name, long amount) {}
}
