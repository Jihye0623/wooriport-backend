package com.wooriport.core_api.base.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
public class GoalConfirmRequestDto {

    // 목표 기본 정보
    @NotBlank
    private String goalType;    // WEDDING / TRAVEL / OTHER
    @NotBlank private String title;       // "결혼 자금"
    @NotNull private LocalDate deadline;
    @NotNull  private Long targetAmount;  // STEP 2 산정 금액
    @NotNull  private Long initialCapital; // STEP 3 초기 자본금
    @NotNull  private UUID sourceAssetId;  // STEP 3 가져올 계좌

    // 포트폴리오 비율 (STEP 5 확정값)
    @NotNull private Integer stockRatio;
    @NotNull private Integer bondRatio;
    @NotNull private Integer cashRatio;

    // 계좌 연동 (nullable)
    private UUID stockAssetId;
    private UUID bondAssetId;
    private UUID depositAssetId;

    // 월 예산 (STEP 4)
    private List<BudgetItem> budgets;

    @Getter
    public static class BudgetItem {
        private String category;   // 식비, 생활비 등
        private Long amount;       // 금액
        private Integer ratio;     // 비율
    }
}