package com.wooriport.core_api.base.dto.consultant;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConsultantAnalyzeRequestDto {

    @NotBlank(message = "목표를 입력해 주세요.")
    private String userGoal;
}
