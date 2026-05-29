package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.portfolio.InvestAmountUpdateRequestDto;
import com.wooriport.core_api.base.dto.portfolio.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.portfolio.PortfolioUpdateRequestDto;
import com.wooriport.core_api.domain.PortfolioFlowItems;
import com.wooriport.core_api.domain.PortfolioFlows;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.domain.common.AssetCategory;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioFlowRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PortfolioFlowRepository portfolioFlowRepository;

    // ──────────────────────────────────────
    // GET /portfolios
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public PortfolioListResponseDto getPortfolios(UUID userId) {

        List<Portfolios> portfolios = portfolioRepository.findByUserId(userId);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        return toResponse(portfolios, user.getMonthlyInvestAmount(), user.getSalary());
    }

    // ──────────────────────────────────────
    // POST /portfolios
    // ──────────────────────────────────────
    @Transactional
    public PortfolioListResponseDto savePortfolios(UUID userId, PortfolioUpdateRequestDto request) {

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 투자할 돈 + 월급 저장
        user.updateMonthlyInvestAmount(request.getMonthlyInvestAmount());
        if (request.getSalary() != null) {
            user.updateSalary(request.getSalary());
        }

        // 기존 삭제 후 재생성
        portfolioRepository.deleteByUserId(userId);

        List<Portfolios> saved = request.getPortfolios().stream()
                .map(item -> {
                    com.wooriport.core_api.domain.Assets asset = null;
                    if (item.getAssetId() != null) {
                        asset = assetRepository.findById(item.getAssetId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "계좌를 찾을 수 없습니다: " + item.getAssetId()));
                        if (item.getAccountPurpose() != null) {
                            asset.updateAccountPurpose(item.getAccountPurpose());
                        }
                    }

                    return Portfolios.builder()
                            .user(user)
                            .assetType(AssetCategory.valueOf(item.getAssetType()))
                            .assetAmount(item.getAssetAmount())
                            .asset(asset)
                            .build();
                })
                .collect(Collectors.toList());

        portfolioRepository.saveAll(saved);

        log.info("[PortfolioService] 포트폴리오 수정 완료 — userId: {}, {}개, monthlyInvestAmount: {}",
                userId, saved.size(), user.getMonthlyInvestAmount());

        return toResponse(saved, user.getMonthlyInvestAmount(), user.getSalary());
    }

    // ──────────────────────────────────────
    // PATCH /portfolios
    // ──────────────────────────────────────
    @Transactional
    public PortfolioListResponseDto updatePortfolios(UUID userId, InvestAmountUpdateRequestDto request) {

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        Long salary = user.getSalary();
        if (salary != null && request.getMonthlyInvestAmount() > salary) {
            throw new IllegalArgumentException("월 투자 금액이 월급을 초과할 수 없습니다.");
        }

        Long oldMonthlyInvestAmount = user.getMonthlyInvestAmount();
        List<Portfolios> portfolios = portfolioRepository.findByUserId(userId);

        if (request.getPortfolios() != null && !request.getPortfolios().isEmpty()) {
            Map<UUID, InvestAmountUpdateRequestDto.PortfolioAmountItem> itemMap = request.getPortfolios().stream()
                    .collect(Collectors.toMap(
                            InvestAmountUpdateRequestDto.PortfolioAmountItem::getAssetId,
                            item -> item));

            for (Portfolios p : portfolios) {
                if (p.getAsset() == null) continue;
                InvestAmountUpdateRequestDto.PortfolioAmountItem item = itemMap.get(p.getAsset().getId());
                if (item == null) continue;
                p.updateRatio(item.getAssetAmount());
                if (item.getAccountPurpose() != null) {
                    p.getAsset().updateAccountPurpose(item.getAccountPurpose());
                }
            }
        } else {
            // portfolios 미전달: 기존 비율로 자동 재계산
            Long current = user.getMonthlyInvestAmount();
            if (current == null || current == 0 || portfolios.isEmpty()) {
                user.updateMonthlyInvestAmount(request.getMonthlyInvestAmount());
                return toResponse(portfolios, request.getMonthlyInvestAmount(), salary);
            }

            long allocated = 0;
            for (int i = 0; i < portfolios.size() - 1; i++) {
                long newAmount = request.getMonthlyInvestAmount() * portfolios.get(i).getAssetAmount() / current;
                portfolios.get(i).updateRatio(newAmount);
                allocated += newAmount;
            }
            // 마지막 항목에 나머지 배정 (반올림 오차 처리)
            portfolios.get(portfolios.size() - 1).updateRatio(request.getMonthlyInvestAmount() - allocated);
        }

        // monthlyInvestAmount 변경 시 portfolioFlow.amount를 기존 비율 그대로 재계산
        if (oldMonthlyInvestAmount != null && oldMonthlyInvestAmount > 0
                && !request.getMonthlyInvestAmount().equals(oldMonthlyInvestAmount)) {
            List<PortfolioFlows> flows = portfolioFlowRepository.findAllByUserIdWithItems(userId);
            for (PortfolioFlows flow : flows) {
                if (flow.getAmount() != null) {
                    long newFlowAmount = flow.getAmount() * request.getMonthlyInvestAmount() / oldMonthlyInvestAmount;
                    flow.updateAmount(newFlowAmount);
                }
            }
        }

        user.updateMonthlyInvestAmount(request.getMonthlyInvestAmount());

        log.info("[PortfolioService] 투자 금액 변경 — userId: {}, {}원 → {}원",
                userId, oldMonthlyInvestAmount, request.getMonthlyInvestAmount());

        return toResponse(portfolios, request.getMonthlyInvestAmount(), salary);
    }

    // ──────────────────────────────────────
    // 공통 변환
    // ──────────────────────────────────────
    private PortfolioListResponseDto toResponse(List<Portfolios> portfolios, Long monthlyInvestAmount, Long salary) {

        Long totalAmount = portfolios.stream()
                .mapToLong(Portfolios::getAssetAmount)
                .sum();

        List<PortfolioListResponseDto.PortfolioItem> items = portfolios.stream()
                .map(p -> PortfolioListResponseDto.PortfolioItem.builder()
                        .id(p.getId())
                        .assetType(p.getAssetType().name())
                        .assetAmount(p.getAssetAmount())
                        .isLinked(p.isLinked())
                        .institution(p.isLinked() ? p.getAsset().getInstitution() : null)
                        .assetNumber(p.isLinked() ? p.getAsset().getAssetNumber() : null)
                        .balance(p.isLinked() ? p.getAsset().getBalance() : null)
                        .accountPurpose(p.isLinked() ? p.getAsset().getAccountPurpose() : null)
                        .build())
                .collect(Collectors.toList());

        return PortfolioListResponseDto.builder()
                .portfolios(items)
                .totalAmount(totalAmount)
                .monthlyInvestAmount(monthlyInvestAmount)
                .salary(salary)
                .build();
    }
}