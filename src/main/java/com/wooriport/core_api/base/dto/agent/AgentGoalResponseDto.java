package com.wooriport.core_api.base.dto.agent;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentGoalResponseDto {

    private String createdAt;
    private String title;
    private String targetAmount;
    private String deadline;
}
