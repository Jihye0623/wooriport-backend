package com.wooriport.core_api.base.dto.asset;

import lombok.Getter;
import java.util.List;

@Getter
public class AssetSyncRequestDto {

    // 선택한 계좌번호 목록
    // null 또는 빈 리스트 → 전체 연동
    private List<String> selectedAssetNumbers;
}
