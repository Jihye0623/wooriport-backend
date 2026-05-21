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
}