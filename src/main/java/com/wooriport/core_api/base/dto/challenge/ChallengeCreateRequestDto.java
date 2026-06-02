package com.wooriport.core_api.base.dto.challenge;

import com.wooriport.core_api.domain.MiniChallenges.ChallengeType;
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

    @NotNull
    private ChallengeType challengeType;

    @NotNull
    private Long target;

    @NotNull
    private Long estimatedSaving;

    @NotBlank
    private String ticker;
}
