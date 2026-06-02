package com.wooriport.core_api.base.dto.agent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import java.util.List;

@Getter
public class AgentProfileRequestDto {

    @NotNull(message = "설문 답변을 입력해주세요.")
    @Size(min = 10, max = 10, message = "10개 문항에 모두 답변해주세요.")
    private List<String> answers;  // ["A","B","A","B","A","B","A","B","B","B"]

    @Size(max = 3, message = "관심 주식 테마는 최대 3개까지 입력 가능합니다.")
    private List<String> stockThemes;  // index 0=1순위, 1=2순위, 2=3순위

    private String lifeGoal;
}