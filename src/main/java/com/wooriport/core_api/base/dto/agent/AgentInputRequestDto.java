package com.wooriport.core_api.base.dto.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AgentInputRequestDto {

    @NotBlank(message = "이벤트 내용을 입력해주세요.")
    private String userInput;  // "일본 여행 가고 싶어. 12월까지 150만원 모으고 싶어"
}
