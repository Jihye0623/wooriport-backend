package com.wooriport.core_api.base.dto.transaction;

import java.util.List;

/**
 * batchPersist() 결과: 후속처리(챌린지/급여)에 넘길 적재분 + 중복 감지 수.
 * duplicates 는 poll 단위 중복(intra-batch + DB 선검사 모두 합산).
 */
public record BatchPersistResult(List<PersistedTransaction> persisted, int duplicates) {}
