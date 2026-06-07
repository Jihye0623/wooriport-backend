package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SalaryService {

    private final TransferPlanService transferPlanService;
    private final FaultInjector faultInjector;

    /**
     * 급여 입금이면 이체 계획을 자동 생성한다. (급여가 아니거나 자동이체 계좌가 아니면 아무 것도 안 함)
     * generateFromSalary 는 미확인 플랜 delete+재생성이라 재실행해도 멱등(이체계획 중복 없음).
     */
    public void handleIfSalary(PersistedTransaction tx) {
        if (!tx.isIncome() || !isSalary(tx.category())) return;
        if (!tx.assetId().equals(tx.autoTransferToAssetId())) return;

        faultInjector.maybeFailSalary();   // chaos: 급여 처리 강제 실패(재처리 토픽 검증용)

        transferPlanService.generateFromSalary(tx.userId());
        log.info("[SalaryService] 급여 감지 → 이체 계획 생성 — userId: {}", tx.userId());
    }

    private boolean isSalary(String category) {
        if (category == null) return false;
        return category.contains("급여")
                || category.contains("월급")
                || category.contains("임금")
                || category.contains("salary");
    }
}
