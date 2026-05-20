package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transfer.TransferPlanCreateRequestDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanListResponseDto;
import com.wooriport.core_api.base.dto.transfer.TransferPlanUpdateRequestDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.TransferPlans;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TransferPlanRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferPlanService {

    private final TransferPlanRepository transferPlanRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    // ──────────────────────────────────────
    // GET /transfer-plans
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public TransferPlanListResponseDto getTransferPlans(UUID userId, int year, int month) {
        List<TransferPlans> plans = transferPlanRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        return toListResponse(plans);
    }

    // ──────────────────────────────────────
    // POST /transfer-plans
    // ──────────────────────────────────────
    @Transactional
    public TransferPlanListResponseDto createTransferPlans(UUID userId, TransferPlanCreateRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 동일 연월 기존 계획 soft delete 후 재생성
        transferPlanRepository.deleteByUserIdAndYearAndMonth(
                userId, request.getYear(), request.getMonth());

        List<TransferPlans> saved = request.getPlans().stream().map(item -> {
            Assets asset = assetRepository.findById(item.getAssetId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "계좌를 찾을 수 없습니다: " + item.getAssetId()));

            return transferPlanRepository.save(
                    TransferPlans.builder()
                            .user(user)
                            .asset(asset)
                            .assetType(Portfolios.AssetType.valueOf(item.getAssetType()))
                            .plannedAmount(item.getPlannedAmount())
                            .isConfirmed(false)
                            .scheduledDate(request.getScheduledDate())
                            .year(request.getYear())
                            .month(request.getMonth())
                            .transferScope(TransferPlans.TransferScope.valueOf(item.getTransferScope()))
                            .build());
        }).collect(Collectors.toList());

        return toListResponse(saved);
    }

    // ──────────────────────────────────────
    // PATCH /transfer-plans/{id}
    // ──────────────────────────────────────
    @Transactional
    public void updateTransferPlan(UUID userId, UUID planId, TransferPlanUpdateRequestDto request) {
        TransferPlans plan = transferPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException("이체 계획을 찾을 수 없습니다."));

        if (request.getPlannedAmount() != null) {
            plan.updatePlannedAmount(request.getPlannedAmount()); // 내부에서 isConfirmed = false
        }
    }

    // ──────────────────────────────────────
    // POST /transfer-plans/confirm-all
    // ──────────────────────────────────────
    @Transactional
    public void confirmAll(UUID userId, int year, int month) {
        List<TransferPlans> plans = transferPlanRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        if (plans.isEmpty()) {
            throw new IllegalStateException("확인할 이체 계획이 없습니다.");
        }

        plans.forEach(TransferPlans::confirm);
    }

    // ──────────────────────────────────────
    // 공통: Entity → Response 변환
    // ──────────────────────────────────────
    private TransferPlanListResponseDto toListResponse(List<TransferPlans> plans) {
        long totalAmount = plans.stream()
                .mapToLong(TransferPlans::getPlannedAmount)
                .sum();

        List<TransferPlanListResponseDto.PlanItem> items = plans.stream()
                .map(p -> TransferPlanListResponseDto.PlanItem.builder()
                        .id(p.getId())
                        .assetId(p.getAsset().getId())
                        .institution(p.getAsset().getInstitution())
                        .assetType(p.getAssetType().name())
                        .plannedAmount(p.getPlannedAmount())
                        .isConfirmed(p.getIsConfirmed())
                        .transferScope(p.getTransferScope().name())
                        .scheduledDate(p.getScheduledDate())
                        .year(p.getYear())
                        .month(p.getMonth())
                        .build())
                .collect(Collectors.toList());

        return TransferPlanListResponseDto.builder()
                .plans(items)
                .totalAmount(totalAmount)
                .build();
    }
}