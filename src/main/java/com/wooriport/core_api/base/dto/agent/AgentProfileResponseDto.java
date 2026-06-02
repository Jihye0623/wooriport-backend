package com.wooriport.core_api.base.dto.agent;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AgentProfileResponseDto {

    // porTI 결과
    private String portiType;           // SWIMMING
    private String portiTypeName;        // 수영
    private String portiDescription;     // 기본기에 충실한, 레인을 벗어나지 않는 타입

    // 소비 요약
    private Long monthlyAvgExpense;      // 월 평균 소비 총액

    // 카테고리별 소비 (도넛 차트용)
    private List<CategoryExpenseItem> categoryExpense;

    // 고정 지출 (통신/공과금/보험료)
    private List<FixedExpenseItem> fixedExpense;
    private Long totalFixedExpense;

    // 투자 성향 매칭
    private InvestTendency investTendency;

    // FastAPI 생성 코멘트 2개
    private String expenseComment;
    private String investComment;

    // porTI 유형에 매칭된 거장
    private InvestorMasterItem investor;

    @Getter @Builder
    public static class CategoryExpenseItem {
        private String name;     // 식비 / 문화여가 / 온라인쇼핑 / 교통
        private Long amount;     // 월평균 금액
        private Integer ratio;   // 전체 대비 비율 (%)
    }

    @Getter @Builder
    public static class FixedExpenseItem {
        private String name;     // 통신비 / 공과금 / 보험료
        private Long amount;
    }

    @Getter @Builder
    public static class InvestTendency {
        private Integer safeRatio;   // 안전 자산 비율 (%)
        private Integer riskRatio;   // 위험 자산 비율 (%)
    }

    @Getter @Builder
    public static class InvestorMasterItem {
        private UUID id;
        private String name;
        private String description;
        private String hashtag1;
        private String hashtag2;
        private String investmentStyle;
        private List<PortfolioItem> items;
    }

    @Getter @Builder
    public static class PortfolioItem {
        private UUID id;
        private String stockName;
        private BigDecimal changeRate;
        private Long sharesHeld;
        private BigDecimal prevQuarterRatio;
        private BigDecimal currentRatio;
        private Integer holdingMonths;
    }
}