package com.wooriport.core_api.base.dto.transaction;

import java.util.UUID;

/**
 * 거래 적재(TransactionService.persist)의 결과를 후속 반응(챌린지/급여)에 넘기기 위한 값 객체.
 * JPA 엔티티를 직접 넘기지 않고 필요한 값만 추려서 전달한다.
 */
public record PersistedTransaction(
        UUID userId,
        UUID assetId,
        UUID autoTransferToAssetId,
        String category,
        long rawAmount,      // 절대값 (부호 없는 원본 금액)
        boolean isIncome     // 원본 amount > 0 (입금 여부)
) {
}
