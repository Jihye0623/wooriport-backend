package com.wooriport.core_api.base.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DashboardResponseDto {

    private UserInfo user;
    private AssetsSummary assetsSummary;
    private SalaryPlan salaryPlan;
    private List<EventItem> events;
    private Consumption consumption;
    private List<PortfolioItem> portfolio;

    @Getter
    @Builder
    public static class UserInfo {
        private UUID id;
        private String name;
    }

    @Getter
    @Builder
    public static class AssetsSummary {
        private Long totalBalance;
        private Long investmentBalance;
        private Long cashBalance;
    }

    @Getter
    @Builder
    public static class SalaryPlan {
        private Long monthlyIncome;
        private List<Allocation> allocations;
    }

    @Getter
    @Builder
    public static class Allocation {
        private String purpose;
        private Long plannedAmount;
    }

    @Getter
    @Builder
    public static class EventItem {
        private UUID id;
        private String title;
        private Long targetAmount;
        private Long currentAmount;
        private LocalDate deadline;
        private String status;
    }

    @Getter
    @Builder
    public static class Consumption {
        private int referenceMonth;
        private Long totalExpense;
        private Boolean isBudgetExceeded;
        private int budgetExceedRate;
        private List<CategoryExpense> categories;
    }

    @Getter
    @Builder
    public static class CategoryExpense {
        private String categoryName;
        private Long expenseAmount;
        private int percentage;
    }

    @Getter
    @Builder
    public static class PortfolioItem {
        private String assetType;
        private Long assetAmount;
    }
}
