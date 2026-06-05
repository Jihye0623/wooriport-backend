package com.wooriport.core_api.base.dto.challenge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ChallengeProposalResponseDto {

    @JsonProperty("created_at")
    private String createdAt;

    private String title;

    private String description;

    private String category;

    @JsonProperty("challenge_sub_type")
    private String challengeSubType;

    @JsonProperty("challenge_type")
    private String challengeType;

    private Long target;

    @JsonProperty("estimated_saving")
    private Long estimatedSaving;

    private String ticker;
}
