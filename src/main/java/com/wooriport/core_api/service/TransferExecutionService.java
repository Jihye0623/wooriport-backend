package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transfer.TransferExecutionListResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferExecutionResponseDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.TransferExecutions;
import com.wooriport.core_api.domain.TransferPlans;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TransferExecutionRepository;
import com.wooriport.core_api.repository.TransferPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferExecutionService {

    private final TransferPlanRepository transferPlanRepository;
    private final TransferExecutionRepository transferExecutionRepository;
    private final AssetRepository assetRepository;

    // ──────────────────────────────────────
    // GET /transfer-executions
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public TransferExecutionListResponseDto getExecutions(UUID userId, int year, int month) {
        List<TransferExecutions> executions = transferExecutionRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        long totalAmount = executions.stream()
                .filter(e -> e.getStatus() == TransferExecutions.ExecutionStatus.COMPLETED)
                .mapToLong(TransferExecutions::getAmount)
                .sum();

        List<TransferExecutionListResponseDto.ExecutionItem> items = executions.stream()
                .map(e -> TransferExecutionListResponseDto.ExecutionItem.builder()
                        .id(e.getId())
                        .fromInstitution(e.getFromAsset().getInstitution())
                        .toInstitution(e.getToAsset().getInstitution())
                        .assetType(e.getPlan().getAssetType().name())
                        .amount(e.getAmount())
                        .status(e.getStatus().name())
                        .executedAt(e.getExecutedAt())
                        .build())
                .collect(Collectors.toList());

        return TransferExecutionListResponseDto.builder()
                .executions(items)
                .totalCount(items.size())
                .totalAmount(totalAmount)
                .build();
    }

    // ──────────────────────────────────────
    // POST /transfer-executions/execute
    // ──────────────────────────────────────
    // ※ 핵심: 아래 3가지를 하나의 @Transactional로 처리
    //   1. 급여 통장(SALARY) balance 차감
    //   2. 목적 통장 balance 증가
    //   3. transfer_executions 저장
    // ──────────────────────────────────────
    @Transactional
    public TransferExecutionResponseDto execute(UUID userId, int year, int month) {

        // 1. is_confirmed = TRUE 인 계획만
        List<TransferPlans> confirmedPlans = transferPlanRepository
                .findByUserIdAndYearAndMonthAndIsConfirmedTrue(userId, year, month);

        if (confirmedPlans.isEmpty()) {
            throw new IllegalStateException(
                    "확인된 이체 계획이 없습니다. confirm-all을 먼저 호출해주세요.");
        }

        // 2. 급여 통장 조회

        Assets salaryAsset = assetRepository
                .findByUserIdAndIsSalaryTrue(userId)
                .orElseThrow(() -> new IllegalStateException("급여 통장이 설정되지 않았습니다."));

        // 3. 잔액 검증
        long totalAmount = confirmedPlans.stream()
                .mapToLong(TransferPlans::getPlannedAmount)
                .sum();

        if (salaryAsset.getBalance() < totalAmount) {
            throw new IllegalStateException(
                    "잔액이 부족합니다. 현재 잔액: " + salaryAsset.getBalance()
                            + "원, 필요 금액: " + totalAmount + "원");
        }

        // 4. 계좌별 이체 실행
        List<TransferExecutionResponseDto.ExecutionResult> results = new ArrayList<>();
        int executedCount = 0;
        int failedCount = 0;

        for (TransferPlans plan : confirmedPlans) {
            TransferExecutions execution = TransferExecutions.builder()
                    .plan(plan)
                    .user(plan.getUser())
                    .fromAsset(salaryAsset)
                    .toAsset(plan.getAsset())
                    .amount(plan.getPlannedAmount())
                    .build();

            try {
                // 출금 (급여 통장)
                salaryAsset.updateBalance(
                        salaryAsset.getBalance() - plan.getPlannedAmount());

                // 입금 (목적 통장)
                Assets toAsset = plan.getAsset();
                toAsset.updateBalance(
                        toAsset.getBalance() + plan.getPlannedAmount());

                execution.complete();
                TransferExecutions saved = transferExecutionRepository.save(execution);
                executedCount++;

                results.add(TransferExecutionResponseDto.ExecutionResult.builder()
                        .executionId(saved.getId())
                        .toAssetId(toAsset.getId())
                        .institution(toAsset.getInstitution())
                        .amount(plan.getPlannedAmount())
                        .status("COMPLETED")
                        .build());

            } catch (Exception e) {
                log.error("이체 실패 planId={}, 사유={}", plan.getId(), e.getMessage());
                execution.fail();
                failedCount++;

                TransferExecutions savedFailed = transferExecutionRepository.save(execution);

                results.add(TransferExecutionResponseDto.ExecutionResult.builder()
                        .executionId(savedFailed.getId())  // ✅
                        .toAssetId(plan.getAsset().getId())
                        .institution(plan.getAsset().getInstitution())
                        .amount(plan.getPlannedAmount())
                        .status("FAILED")
                        .failReason(e.getMessage())
                        .build());
            }
        }
        confirmedPlans.forEach(TransferPlans::resetConfirm);

        return TransferExecutionResponseDto.builder()
                .executedCount(executedCount)
                .failedCount(failedCount)
                .totalAmount(totalAmount)
                .results(results)
                .build();
    }
}