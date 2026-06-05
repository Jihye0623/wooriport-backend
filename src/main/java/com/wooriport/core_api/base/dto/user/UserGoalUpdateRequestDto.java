package com.wooriport.core_api.base.dto.user;

import lombok.Getter;

import java.util.List;

@Getter
public class UserGoalUpdateRequestDto {
    private List<String> stockThemes;
    private String lifeGoal;
}
