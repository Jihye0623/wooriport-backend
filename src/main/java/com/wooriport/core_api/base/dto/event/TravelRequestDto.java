package com.wooriport.core_api.base.dto.event;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.time.LocalDate;

@Getter
public class TravelRequestDto {
    @NotNull(message = "목표 마감일을 입력해주세요.")
    private LocalDate deadline;

    @NotNull(message = "최대 예산을 입력해주세요.")
    private Long maximumBudget;

    @NotBlank(message = "여행 나라를 입력해주세요.")
    private String destination;

    @NotBlank(message = "여행 스타일을 선택해주세요.")
    private String travelStyle;               // budget / luxury

    @NotNull
    @Min(1)
    private Integer travelDays;

    @NotBlank(message = "출발 예정 시기를 입력해주세요.")
    private String departureMonth;            // "2025-08"
}