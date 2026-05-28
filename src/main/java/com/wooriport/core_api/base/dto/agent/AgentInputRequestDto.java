package com.wooriport.core_api.base.dto.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AgentInputRequestDto {

    @NotBlank(message = "목표 이름을 입력해주세요.")
    private String title;

    @NotBlank(message = "목표 금액을 입력해주세요.")
    private String targetAmount;

    @NotBlank(message = "목표 마감일을 입력해주세요.")
    private String deadline;
}
