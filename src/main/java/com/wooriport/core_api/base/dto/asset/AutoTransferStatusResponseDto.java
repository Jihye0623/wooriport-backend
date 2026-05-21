package com.wooriport.core_api.base.dto.asset;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AutoTransferStatusResponseDto {
    private Boolean isConnected;                  // 자동이체 연결 여부
    private List<String> restrictedFeatures;      // 미연결 시 제한 기능 목록

    // 연결된 경우 계좌 정보
    private UUID fromAssetId;
    private String fromInstitution;               // 급여 통장 금융사
}
