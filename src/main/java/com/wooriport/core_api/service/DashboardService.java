package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.dashboard.DashboardResponseDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Event;
import com.wooriport.core_api.domain.PortfolioFlowItems;
import com.wooriport.core_api.domain.PortfolioFlows;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.ProductCategoryRate;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.EventRepository;
import com.wooriport.core_api.repository.PortfolioFlowItemRepository;
import com.wooriport.core_api.repository.PortfolioFlowRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
import com.wooriport.core_api.repository.ProductCategoryRateRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final PortfolioFlowRepository portfolioFlowRepository;
    private final PortfolioFlowItemRepository portfolioFlowItemRepository;
    private final EventRepository eventRepository;
    private final TransactionRepository transactionRepository;
    private final ProductCategoryRateRepository productCategoryRateRepository;

    // assets.assetType → 현금성 / 투자자산 분류
    private static final Set<Assets.AccountType> CASH_TYPES = Set.of(
            Assets.AccountType.CHECKING,
            Assets.AccountType.PARKING,
            Assets.AccountType.SAVINGS,
            Assets.AccountType.DEPOSIT,
            Assets.AccountType.CMA);
    private static final Set<Assets.AccountType> INVESTMENT_TYPES = Set.of(
            Assets.AccountType.STOCK,
            Assets.AccountType.IRP,
            Assets.AccountType.ISA);

    @Transactional(readOnly = true)
    public DashboardResponseDto getDashboard(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);
        List<Portfolios> portfolios = portfolioRepository.findByUserId(userId);
        List<PortfolioFlowItems> flowPutItems = portfolioFlowItemRepository.findAllPutByUserIdWithAsset(userId);
        List<Event> events = eventRepository.findActiveDashboardEvents(userId);

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        List<Object[]> categoryRows = transactionRepository.sumExpenseGroupByCategory(userId, year, month);
        List<Transactions> monthlyExpenses = transactionRepository.findMonthlyExpenses(userId, year, month);

        Map<String, String> rateByLabel = productCategoryRateRepository.findAll().stream()
                .collect(Collectors.toMap(
                        ProductCategoryRate::getCategoryLabel,
                        r -> r.getRate() == null ? "-" : r.getRate(),
                        (a, b) -> a));

        return DashboardResponseDto.builder()
                .user(buildUser(user))
                .assetsSummary(buildAssetsSummary(assets))
                .salaryPlan(buildSalaryPlan(user, portfolios))
                .events(buildEvents(userId, events, today))
                .consumption(buildConsumption(month, categoryRows, monthlyExpenses, portfolios))
                .portfolio(buildPortfolio(flowPutItems, rateByLabel))
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

    // currentAmount = portfolio_flows.gatheringAsset.balance, progressRate = current / target * 100
    private List<DashboardResponseDto.EventItem> buildEvents(UUID userId, List<Event> events, LocalDate today) {
        return events.stream()
                .map(e -> {
                    Long current = portfolioFlowRepository.findByUserIdAndEventId(userId, e.getId())
                            .map(PortfolioFlows::getGatheringAsset)
                            .map(a -> a == null ? 0L : (a.getBalance() == null ? 0L : a.getBalance()))
                            .orElse(0L);
                    Long target = e.getTargetAmount();
                    int progress = (target != null && target > 0)
                            ? (int) Math.min(100, Math.round(current * 100.0 / target))
                            : 0;
                    int dday = e.getDeadline() == null
                            ? 0
                            : (int) ChronoUnit.DAYS.between(today, e.getDeadline());
                    return DashboardResponseDto.EventItem.builder()
                            .id(e.getId())
                            .title(e.getTitle())
                            .targetAmount(target)
                            .currentAmount(current)
                            .progressRate(progress)
                            .deadline(e.getDeadline())
                            .dday(dday)
                            .status(e.getStatus().name())
                            .build();
                })
                .toList();
    }

    private DashboardResponseDto.Consumption buildConsumption(
            int month,
            List<Object[]> categoryRows,
            List<Transactions> monthlyExpenses,
            List<Portfolios> portfolios) {

        long totalExpense = categoryRows.stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();

        long totalBudget = portfolios.stream()
                .mapToLong(p -> p.getAssetAmount() == null ? 0L : p.getAssetAmount())
                .sum();

        boolean isBudgetExceeded = totalBudget > 0 && totalExpense > totalBudget;
        int budgetExceedRate = (totalBudget > 0 && isBudgetExceeded)
                ? (int) Math.round(((totalExpense - totalBudget) * 100.0) / totalBudget)
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
                .isBudgetExceeded(isBudgetExceeded)
                .budgetExceedRate(budgetExceedRate)
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
