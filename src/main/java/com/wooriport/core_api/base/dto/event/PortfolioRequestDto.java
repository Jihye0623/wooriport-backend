package com.wooriport.core_api.base.dto.event;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.time.LocalDate;

// ─────────────────────────────────────────
// POST /agent/goal/portfolio
// ─────────────────────────────────────────
@Getter
public class PortfolioRequestDto {
    @NotNull(message = "목표 마감일을 입력해주세요.")
    private LocalDate deadline;

    @NotNull(message = "초기 자본금을 입력해주세요.")
    private Long initialCapital;              // STEP 3에서 설정한 초기 자본금

    @NotNull(message = "월 투자금을 입력해주세요.")
    private Long monthlySeed;                 // STEP 4에서 입력한 월 투자금

    @NotNull(message = "목표 금액을 입력해주세요.")
    private Long targetAmount;               // STEP 2에서 산정된 예산 총액
}
