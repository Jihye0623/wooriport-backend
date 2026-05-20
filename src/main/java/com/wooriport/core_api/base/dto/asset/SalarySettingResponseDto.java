package com.wooriport.core_api.base.dto.asset;

import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class SalarySettingResponseDto {
    private UUID assetId;
    private String institution;
    private Boolean isWooriBank;  // 프론트가 이걸 보고 다음 단계 분기
}