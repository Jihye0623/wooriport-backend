package com.wooriport.core_api.base.dto.asset;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class MydataInstitutionsResponseDto {

    private List<InstitutionItem> institutions;
    private int totalCount;

    @Getter
    @Builder
    public static class InstitutionItem {
        private String institution;   // 기관명 (preview 필터 기준)
        private String bankType;      // WOORI / OTHER
        private int accountCount;     // 해당 기관의 연동 가능 계좌 수
    }
}
