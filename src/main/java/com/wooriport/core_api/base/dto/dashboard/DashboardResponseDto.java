package com.wooriport.core_api.base.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DashboardResponseDto {

    private UserInfo user;
    private AssetsSummary assetsSummary;
    private SalaryPlan salaryPlan;
    private Consumption consumption;
    private List<PortfolioItem> portfolio;
    private TaxSaving taxSaving;

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
    public static class Consumption {
        private int referenceMonth;
        private Long totalExpense;          // 이번 달 소비총합
        private Long lastMonthExpense;      // 저번 달 소비총합 (예산 기준선)
        private Boolean isBudgetExceeded;   // 이번 달 > 저번 달
        private int budgetExceedRate;       // 저번 달 대비 초과율 (%)
        private List<Long> weeklyExpenses;  // 이번 주 요일별 지출 [월,화,수,목,금,토,일]
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

    @Getter
    @Builder
    public static class TaxSaving {
        // 적용 공제율(%) 16.5 / 13.2
        private Double deductionRate;
        // 연금저축 + IRP 합산 예상 세액공제액 ("13월의 월급")
        private Long totalTaxDeduction;
        // 합산 한도까지 남은 납입 여력
        private Long remaining;
        // IRP / 연금저축 납입 현황 바
        private List<TaxSavingBar> bars;
    }

    @Getter
    @Builder
    public static class TaxSavingBar {
        // "IRP" / "연금저축"
        private String label;
        // 현재 납입액 (= 잔액 합)
        private Long contribution;
        // 실제 공제 대상액 (합산 900만·연금 600만 한도 반영) — 합산 바 채움에 사용
        private Long deductible;
        // 세액공제 한도 (정책 상수: 연금 600만 / IRP 합산 900만)
        private Long limit;
    }
}
