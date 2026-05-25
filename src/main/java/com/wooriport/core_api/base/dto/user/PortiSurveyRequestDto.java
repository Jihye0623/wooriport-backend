package com.wooriport.core_api.base.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.util.List;

@Getter
public class PortiSurveyRequestDto {

    // 10개 문항 답변 목록
    // 각 문항 답변은 "A" 또는 "B"
    @NotNull(message = "설문 답변을 입력해주세요.")
    private List<String> answers;  // ["A", "B", "A", "B", "A", "B", "A", "B", "A", "B"]

    public PortiSurveyRequestDto(List<String> answers) {
        this.answers = answers;
    }
}