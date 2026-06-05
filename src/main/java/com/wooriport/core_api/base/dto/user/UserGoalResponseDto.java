package com.wooriport.core_api.base.dto.user;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UserGoalResponseDto {
    private List<String> stockThemes;
    private String lifeGoal;
}
