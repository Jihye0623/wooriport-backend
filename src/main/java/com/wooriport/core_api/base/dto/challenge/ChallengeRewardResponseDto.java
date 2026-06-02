package com.wooriport.core_api.base.dto.challenge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ChallengeRewardResponseDto {

    @JsonProperty("created_at")
    private String createdAt;

    private String message;

    @JsonProperty("stock_name")
    private String stockName;

    private String ticker;

    @JsonProperty("current_price")
    private Integer currentPrice;

    private Double shares;
}
