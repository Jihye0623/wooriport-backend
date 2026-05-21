package com.wooriport.core_api.base.dto.asset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ScheduledDateRequestDto {
    @NotNull(message = "이체일을 입력해주세요.")
    @Min(value = 1, message = "이체일은 1일 이상이어야 합니다.")
    @Max(value = 28, message = "이체일은 28일 이하여야 합니다.")  // 매월 존재하는 날짜 기준
    private Integer scheduledDate;
}
