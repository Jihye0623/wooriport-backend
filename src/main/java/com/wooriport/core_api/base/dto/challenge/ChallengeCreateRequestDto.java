package com.wooriport.core_api.base.dto.challenge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ChallengeCreateRequestDto {

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String category;

    private Long targetAmount;

    private Integer targetCount;

    private String rewardStockTicker;
}
