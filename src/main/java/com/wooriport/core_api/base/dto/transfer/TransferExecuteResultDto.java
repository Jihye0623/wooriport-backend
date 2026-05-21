package com.wooriport.core_api.base.dto.transfer;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransferExecuteResultDto {
    private int successCount;
    private int failCount;
    private int totalCount;
}