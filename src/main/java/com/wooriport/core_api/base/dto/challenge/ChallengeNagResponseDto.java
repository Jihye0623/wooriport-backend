package com.wooriport.core_api.base.dto.challenge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ChallengeNagResponseDto {

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("nag_message")
    private String nagMessage;
}
