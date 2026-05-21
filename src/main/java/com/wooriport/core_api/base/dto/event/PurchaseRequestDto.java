package com.wooriport.core_api.base.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.time.LocalDate;

@Getter
public class PurchaseRequestDto {
    @NotNull(message = "목표 마감일을 입력해주세요.")
    private LocalDate deadline;

    @NotBlank(message = "구매 희망 물건명을 입력해주세요.")
    private String itemName;
}