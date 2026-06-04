package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SalaryService {

    private final TransferPlanService transferPlanService;

    /**
     * 급여 입금이면 이체 계획을 자동 생성한다. (급여가 아니거나 자동이체 계좌가 아니면 아무 것도 안 함)
     */
    @Async
    public void handleIfSalary(PersistedTransaction tx) {
        try {
            if (!tx.isIncome() || !isSalary(tx.category())) return;
            if (!tx.assetId().equals(tx.autoTransferToAssetId())) return;

            transferPlanService.generateFromSalary(tx.userId());
            log.info("[SalaryService] 급여 감지 → 이체 계획 생성 — userId: {}", tx.userId());
        } catch (Exception e) {
            log.error("[SalaryService] 급여 처리 실패 — userId: {}, 사유: {}", tx.userId(), e.getMessage());
        }
    }

    private boolean isSalary(String category) {
        if (category == null) return false;
        return category.contains("급여")
                || category.contains("월급")
                || category.contains("임금")
                || category.contains("salary");
    }
}
