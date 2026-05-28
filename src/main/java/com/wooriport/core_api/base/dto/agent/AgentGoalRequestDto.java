package com.wooriport.core_api.base.dto.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AgentGoalRequestDto {

    @NotBlank(message = "이벤트 내용을 입력해주세요.")
    private String userInput;
}
