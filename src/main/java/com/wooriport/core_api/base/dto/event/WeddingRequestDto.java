package com.wooriport.core_api.base.dto.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.time.LocalDate;

@Getter
public class WeddingRequestDto {

    @NotNull(message = "목표 마감일을 입력해주세요.")
    private LocalDate deadline;

    @NotBlank(message = "예식 지역을 입력해주세요.")
    private String weddingRegion;             // 서울, 부산 등

    @NotNull
    @Min(1) @Max(12)
    private Integer weddingMonth;             // 예식 시기 (1~12)

    @NotBlank(message = "신혼여행 규모를 선택해주세요.")
    private String honeymoonScale;            // small / medium / large

    @NotBlank(message = "스드메 규모를 선택해주세요.")
    private String sdrmeScale;                // small / medium / large
}
