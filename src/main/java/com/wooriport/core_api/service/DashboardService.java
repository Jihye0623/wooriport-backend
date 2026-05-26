package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.dashboard.DashboardResponseDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Event;
import com.wooriport.core_api.domain.PortfolioItems;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.EventRepository;
import com.wooriport.core_api.repository.PortfolioItemRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;
    private final EventRepository eventRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public DashboardResponseDto getDashboard(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);
        List<Portfolios> portfolios = portfolioRepository.findByUserId(userId);
        List<PortfolioItems> portfolioItems = portfolioItemRepository.findAllByUserIdWithAsset(userId);
        List<Event> events = eventRepository.findActiveDashboardEvents(userId);

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        List<Object[]> categoryRows = transactionRepository.sumExpenseGroupByCategory(userId, year, month);

        return DashboardResponseDto.builder()
                .user(buildUser(user))
                .assetsSummary(buildAssetsSummary(assets, portfolioItems))
                .salaryPlan(buildSalaryPlan(portfolios))
                .events(buildEvents(events))
                .consumption(buildConsumption(month, categoryRows, portfolios))
                .portfolio(buildPortfolio(portfolioItems))
                .build();
    }

    private DashboardResponseDto.UserInfo buildUser(Users user) {
        return DashboardResponseDto.UserInfo.builder()
                .id(user.getId())
                .name(user.getName())
                .build();
    }

    // assets → portfolio_items 의 product_type 으로 현금성/투자자산 분류
    // DEPOSIT = 현금성, STOCK/BOND = 투자자산
    private DashboardResponseDto.AssetsSummary buildAssetsSummary(
            List<Assets> assets, List<PortfolioItems> portfolioItems) {

        long totalBalance = assets.stream().mapToLong(Assets::getBalance).sum();

        Map<UUID, PortfolioItems.ProductType> assetIdToProductType = portfolioItems.stream()
                .filter(p -> p.getAsset() != null)
                .collect(Collectors.toMap(
                        p -> p.getAsset().getId(),
                        PortfolioItems::getProductType,
                        (a, b) -> a));

        long cashBalance = 0L;
        long investmentBalance = 0L;
        for (Assets a : assets) {
            PortfolioItems.ProductType type = assetIdToProductType.get(a.getId());
            if (type == PortfolioItems.ProductType.DEPOSIT) {
                cashBalance += a.getBalance();
            } else if (type == PortfolioItems.ProductType.STOCK
                    || type == PortfolioItems.ProductType.BOND) {
                investmentBalance += a.getBalance();
            }
        }

        return DashboardResponseDto.AssetsSummary.builder()
                .totalBalance(totalBalance)
                .investmentBalance(investmentBalance)
                .cashBalance(cashBalance)
                .build();
    }

    // monthlyIncome = SUM(portfolios.assetAmount)
    // allocations = portfolios 각 행의 (asset.accountPurpose, assetAmount)
    private DashboardResponseDto.SalaryPlan buildSalaryPlan(List<Portfolios> portfolios) {
        long monthlyIncome = portfolios.stream()
                .mapToLong(p -> p.getAssetAmount() == null ? 0L : p.getAssetAmount())
                .sum();

        List<DashboardResponseDto.Allocation> allocations = portfolios.stream()
                .map(p -> DashboardResponseDto.Allocation.builder()
                        .purpose(p.getAsset() != null ? p.getAsset().getAccountPurpose() : null)
                        .plannedAmount(p.getAssetAmount())
                        .build())
                .toList();

        return DashboardResponseDto.SalaryPlan.builder()
                .monthlyIncome(monthlyIncome)
                .allocations(allocations)
                .build();
    }

    private List<DashboardResponseDto.EventItem> buildEvents(List<Event> events) {
        return events.stream()
                .map(e -> DashboardResponseDto.EventItem.builder()
                        .id(e.getId())
                        .title(e.getTitle())
                        .targetAmount(e.getTargetAmount())
                        .currentAmount(e.getCurrentAmount())
                        .deadline(e.getDeadline())
                        .status(e.getStatus().name())
                        .build())
                .toList();
    }

    private DashboardResponseDto.Consumption buildConsumption(
            int month, List<Object[]> categoryRows, List<Portfolios> portfolios) {

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

    // portfolio_items 의 asset.balance × productRatio / 100
    private List<DashboardResponseDto.PortfolioItem> buildPortfolio(
            List<PortfolioItems> portfolioItems) {

        return portfolioItems.stream()
                .map(pi -> {
                    long balance = (pi.getAsset() != null && pi.getAsset().getBalance() != null)
                            ? pi.getAsset().getBalance()
                            : 0L;
                    int ratio = pi.getProductRatio() == null ? 0 : pi.getProductRatio();
                    long amount = balance * ratio / 100;
                    return DashboardResponseDto.PortfolioItem.builder()
                            .assetType(pi.getProductType().name())
                            .assetAmount(amount)
                            .build();
                })
                .toList();
    }
}
