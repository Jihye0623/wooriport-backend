package com.wooriport.core_api.base.dto.event;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class EventCreateRequestDto {

    @NotBlank(message = "목표 이름을 입력해주세요.")
    private String title;

    @NotBlank(message = "목표 금액을 입력해주세요.")
    private String targetAmount;

    @NotBlank(message = "목표 마감일을 입력해주세요.")
    private String deadline;

    @NotBlank(message = "이벤트 내용을 입력해주세요.")
    private String userInput;
}
