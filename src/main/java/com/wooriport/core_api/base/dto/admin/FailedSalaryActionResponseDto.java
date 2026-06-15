package com.wooriport.core_api.base.dto.admin;

import com.wooriport.core_api.domain.FailedSalaryAction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** 관리자 대시보드: DLT 격리된 급여 실패 목록 항목 */
@Getter
@Builder
public class FailedSalaryActionResponseDto {

    private UUID id;
    private UUID userId;
    private Integer attempts;
    private String lastError;
    private String status;
    private LocalDateTime createdAt;

    public static FailedSalaryActionResponseDto from(FailedSalaryAction f) {
        return FailedSalaryActionResponseDto.builder()
                .id(f.getId())
                .userId(f.getUserId())
                .attempts(f.getAttempts())
                .lastError(f.getLastError())
                .status(f.getStatus().name())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
