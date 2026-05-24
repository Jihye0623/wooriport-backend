package com.wooriport.core_api.domain.common;

public enum AssetCategory {
    FIXED,          // 고정비 (생활비)
    EMERGENCY,      // 비상금
    CASH,           // 현금/예금
    STOCK,          // 주식
    BOND,           // 채권
    IRP,            // 개인형 퇴직연금
    DEPOSIT         // 예금/적금 (portfolio_items에서 올 때)
}
