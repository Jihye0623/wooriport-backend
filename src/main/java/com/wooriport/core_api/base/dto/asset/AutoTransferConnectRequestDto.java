package com.wooriport.core_api.base.dto.asset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.util.UUID;

@Getter
public class AutoTransferConnectRequestDto {

    @NotNull(message = "이체 대상 계좌를 선택해주세요.")
    private UUID toAssetId;    // 우리은행 계좌 UUID

    @NotNull(message = "급여일을 입력해주세요.")
    @Min(1) @Max(31)
    private Integer salaryDate; // 급여일 (1~31)
}