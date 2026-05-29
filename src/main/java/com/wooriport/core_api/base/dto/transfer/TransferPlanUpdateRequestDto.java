package com.wooriport.core_api.base.dto.transfer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class TransferPlanUpdateRequestDto {

    @NotNull
    private UUID assetId;

    @NotNull
    @Min(0)
    private Long amount;
}
