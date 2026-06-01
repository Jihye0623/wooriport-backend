package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.portfolio.InvestAmountUpdateRequestDto;
import com.wooriport.core_api.base.dto.portfolio.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.portfolio.PortfolioUpdateRequestDto;
import com.wooriport.core_api.domain.PortfolioFlows;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioFlowRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

        Long oldMonthlyInvestAmount = user.getMonthlyInvestAmount();

        // 기존 포트폴리오 전체 삭제 후 재생성
        portfolioRepository.deleteByUserId(userId);

        List<Portfolios> saved = request.getPortfolios().stream()
                .map(item -> {
                    com.wooriport.core_api.domain.Assets asset = assetRepository.findById(item.getAssetId())
                            .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다: " + item.getAssetId()));
                    if (item.getAccountPurpose() != null) {
                        asset.updateAccountPurpose(item.getAccountPurpose());
                    }
                    return Portfolios.builder()
                            .user(user)
                            .assetAmount(item.getAssetAmount())
                            .asset(asset)
                            .build();
                })
                .collect(Collectors.toList());

        portfolioRepository.saveAll(saved);

        // portfolioFlow.amount를 현재 monthlyInvestAmount 대비 비율로 재계산
        if (oldMonthlyInvestAmount != null && oldMonthlyInvestAmount > 0) {
            List<PortfolioFlows> flows = portfolioFlowRepository.findAllByUserIdWithItems(userId);
            for (PortfolioFlows flow : flows) {
                if (flow.getAmount() != null) {
                    long newFlowAmount = flow.getAmount() * request.getMonthlyInvestAmount() / oldMonthlyInvestAmount;
                    flow.updateAmount(newFlowAmount);
                }
            }
        }

        user.updateMonthlyInvestAmount(request.getMonthlyInvestAmount());

        log.info("[PortfolioService] 포트폴리오 수정 — userId: {}, {}개, {}원 → {}원",
                userId, saved.size(), oldMonthlyInvestAmount, request.getMonthlyInvestAmount());

        return toResponse(saved, request.getMonthlyInvestAmount(), user.getSalary());
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