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
        private Long monthlyIncome;        // users.salary
        private Long investmentAmount;     // users.monthly_invest_amount
        private Long surplus;              // monthlyIncome - sum(allocations) - investmentAmount
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
        private Long currentAmount;     // = portfolio_flows.gatheringAsset.balance
        private Integer progressRate;   // 0~100 (%)
        private LocalDate deadline;
        private Integer dday;           // 오늘 ~ deadline (일)
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
        private String sub;     // ex) "배달의민족 외 24건"
    }

    @Getter
    @Builder
    public static class PortfolioItem {
        // ETF / 현금성 / 적금 / IRP
        private String categoryLabel;
        // 비중 (%) — 전체 포트폴리오 대비
        private Integer ratio;
        // 보유 금액 (sum)
        private Long assetAmount;
        // 수익률 더미 ("+4%", "-" 등)
        private String rate;
        // 카테고리 내부 상품 단위 상세
        private List<PortfolioSubItem> items;
    }

    @Getter
    @Builder
    public static class PortfolioSubItem {
        // 상품 또는 계좌 이름
        private String name;
        // 비중 (%) — 전체 포트폴리오 대비
        private Integer ratio;
        // 카테고리 rate 그대로 ("+4%" 등)
        private String rate;
    }
}
