package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.portfolio.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.portfolio.PortfolioUpdateRequestDto;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.domain.common.AssetCategory;
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

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        return toResponse(portfolios, salaryAmount, user.getMonthlyInvestAmount());
    }

    // ──────────────────────────────────────
    // PATCH /portfolios
    // ──────────────────────────────────────
    @Transactional
    public PortfolioListResponseDto updatePortfolios(UUID userId, PortfolioUpdateRequestDto request) {

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

        Long salaryAmount = transactionRepository
                .findLatestSalaryTransaction(userId)
                .map(tx -> tx.getAmount())
                .orElse(0L);

        log.info("[PortfolioService] 포트폴리오 수정 완료 — userId: {}, {}개, monthlyInvestAmount: {}",
                userId, saved.size(), user.getMonthlyInvestAmount());

        return toResponse(saved, salaryAmount, user.getMonthlyInvestAmount());
    }

    // ──────────────────────────────────────
    // 공통 변환
    // ──────────────────────────────────────
    private PortfolioListResponseDto toResponse(List<Portfolios> portfolios, Long salaryAmount, Long monthlyInvestAmount) {

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
                .build();
    }
}