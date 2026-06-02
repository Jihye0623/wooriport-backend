package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SalaryServiceTest {

    @Mock TransferPlanService transferPlanService;
    @InjectMocks SalaryService salaryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    @Test
    @DisplayName("급여 카테고리가 아니면 이체계획을 생성하지 않는다")
    void notSalaryCategory_doesNotGenerate() {
        salaryService.handleIfSalary(tx("식비", true, assetId));
        verifyNoInteractions(transferPlanService);
    }

    @Test
    @DisplayName("입금이 아니면(출금) 이체계획을 생성하지 않는다")
    void notIncome_doesNotGenerate() {
        salaryService.handleIfSalary(tx("급여", false, assetId));
        verifyNoInteractions(transferPlanService);
    }

    @Test
    @DisplayName("급여 입금이어도 자동이체 계좌가 아니면 생성하지 않는다")
    void salaryButNotAutoTransferAsset_doesNotGenerate() {
        // assetId 와 autoTransferToAssetId 가 다른 경우
        salaryService.handleIfSalary(
                new PersistedTransaction(userId, assetId, UUID.randomUUID(), "급여", 3_000_000L, true));
        verifyNoInteractions(transferPlanService);
    }

    @Test
    @DisplayName("급여 + 입금 + 자동이체 계좌 일치 → 이체계획 생성")
    void salaryIncomeOnAutoTransferAsset_generates() {
        salaryService.handleIfSalary(tx("급여", true, assetId));
        verify(transferPlanService).generateFromSalary(userId);
    }

    /** assetId == autoTransferToAssetId (자동이체 계좌 일치) 인 거래 */
    private PersistedTransaction tx(String category, boolean isIncome, UUID assetId) {
        return new PersistedTransaction(userId, assetId, assetId, category, 3_000_000L, isIncome);
    }
}
