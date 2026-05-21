package com.wooriport.core_api.service;


import com.wooriport.core_api.base.dto.portfolio.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.portfolio.PortfolioUpdateRequestDto;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioRepository;
import com.wooriport.core_api.repository.TransactionRepository;
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
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    // ──────────────────────────────────────
    // GET /portfolios
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public PortfolioListResponseDto getPortfolios(UUID userId) {

        List<Portfolios> portfolios = portfolioRepository.findByUserId(userId);

        // 최근 급여 금액 (없으면 0)
        Long salaryAmount = transactionRepository
                .findLatestSalaryTransaction(userId)
                .map(tx -> tx.getAmount())
                .orElse(0L);

        return toResponse(portfolios, salaryAmount);
    }

    // ──────────────────────────────────────
    // PATCH /portfolios
    // ──────────────────────────────────────
    @Transactional
    public PortfolioListResponseDto updatePortfolios(UUID userId, PortfolioUpdateRequestDto request) {

        // 비율 합계 검증
        int totalRatio = request.getPortfolios().stream()
                .mapToInt(PortfolioUpdateRequestDto.PortfolioItem::getAssetRatio)
                .sum();

        if (totalRatio != 100) {
            throw new IllegalArgumentException(
                    "비율 합계가 100이어야 합니다. 현재: " + totalRatio);
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
                    }

                    return Portfolios.builder()
                            .user(userRepository.getReferenceById(userId))
                            .assetType(Portfolios.AssetType.valueOf(item.getAssetType()))
                            .assetRatio(item.getAssetRatio())
                            .asset(asset)
                            .build();
                })
                .collect(Collectors.toList());

        portfolioRepository.saveAll(saved);

        Long salaryAmount = transactionRepository
                .findLatestSalaryTransaction(userId)
                .map(tx -> tx.getAmount())
                .orElse(0L);

        log.info("[PortfolioService] 포트폴리오 수정 완료 — userId: {}, {}개", userId, saved.size());

        return toResponse(saved, salaryAmount);
    }

    // ──────────────────────────────────────
    // 공통 변환
    // ──────────────────────────────────────
    private PortfolioListResponseDto toResponse(List<Portfolios> portfolios, Long salaryAmount) {
        int totalRatio = portfolios.stream()
                .mapToInt(Portfolios::getAssetRatio)
                .sum();

        List<PortfolioListResponseDto.PortfolioItem> items = portfolios.stream()
                .map(p -> PortfolioListResponseDto.PortfolioItem.builder()
                        .id(p.getId())
                        .assetType(p.getAssetType().name())
                        .assetRatio(p.getAssetRatio())
                        .amount(salaryAmount > 0
                                ? salaryAmount * p.getAssetRatio() / 100
                                : null)
                        .isLinked(p.isLinked())
                        .institution(p.isLinked() ? p.getAsset().getInstitution() : null)
                        .assetNumber(p.isLinked() ? p.getAsset().getAssetNumber() : null)
                        .balance(p.isLinked() ? p.getAsset().getBalance() : null)
                        .build())
                .collect(Collectors.toList());

        return PortfolioListResponseDto.builder()
                .portfolios(items)
                .totalRatio(totalRatio)
                .salaryAmount(salaryAmount)
                .build();
    }
}