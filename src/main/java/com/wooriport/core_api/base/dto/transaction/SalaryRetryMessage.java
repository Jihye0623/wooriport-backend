package com.wooriport.core_api.base.dto.transaction;

/**
 * 급여 처리 실패 시 재처리 토픽(transaction-events.salary.retry)에 싣는 메시지.
 * attempt 로 재시도 횟수를 추적하고, 한계 초과 시 DLT 로 보낸다.
 */
public record SalaryRetryMessage(
        PersistedTransaction tx,
        int attempt
) {
    public SalaryRetryMessage next() {
        return new SalaryRetryMessage(tx, attempt + 1);
    }
}
