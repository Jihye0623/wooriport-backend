package com.wooriport.core_api.service;


import com.wooriport.core_api.base.dto.eventPortfolio.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.eventPortfolio.PortfolioUpdateRequestDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Event;
import com.wooriport.core_api.domain.EventPortfolios;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.EventPortfoliosRepository;
import com.wooriport.core_api.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventPortfolioService {

    private final EventRepository eventRepository;
    private final EventPortfoliosRepository eventPortfoliosRepository;
    private final AssetRepository assetRepository;

    // ──────────────────────────────────────
    // GET /goals/{goalId}/portfolios
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public PortfolioListResponseDto getPortfolios(UUID userId, UUID goalId) {

        Event goal = eventRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new IllegalArgumentException("목표를 찾을 수 없습니다."));

        List<EventPortfolios> portfolios = eventPortfoliosRepository
                .findByEventId(goalId);

        List<PortfolioListResponseDto.PortfolioItem> items = portfolios.stream()
                .map(gp -> PortfolioListResponseDto.PortfolioItem.builder()
                        .id(gp.getId())
                        .productType(gp.getProductType().name())
                        .productRatio(gp.getProductRatio())
                        .isLinked(gp.isLinked())
                        .institution(gp.isLinked() ? gp.getAsset().getInstitution() : null)
                        .assetNumber(gp.isLinked() ? gp.getAsset().getAssetNumber() : null)
                        .balance(gp.isLinked() ? gp.getAsset().getBalance() : null)
                        .build())
                .collect(Collectors.toList());

        int totalRatio = items.stream()
                .mapToInt(PortfolioListResponseDto.PortfolioItem::getProductRatio)
                .sum();

        boolean isRebalanceable = items.stream()
                .allMatch(PortfolioListResponseDto.PortfolioItem::getIsLinked);

        return PortfolioListResponseDto.builder()
                .eventId(goalId)
                .portfolios(items)
                .totalRatio(totalRatio)
                .isRebalanceable(isRebalanceable)
                .build();
    }

    // ──────────────────────────────────────
    // PATCH /goals/{goalId}/portfolios
    // ──────────────────────────────────────
    @Transactional
    public PortfolioListResponseDto updatePortfolios(UUID userId, UUID goalId,
                                                     PortfolioUpdateRequestDto request) {

        eventRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new IllegalArgumentException("목표를 찾을 수 없습니다."));

        // 비율 합계 검증
        int totalRatio = request.getStockRatio()
                + request.getBondRatio()
                + request.getCashRatio();
        if (totalRatio != 100) {
            throw new IllegalArgumentException(
                    "비율 합계가 100이어야 합니다. 현재: " + totalRatio);
        }

        // 기존 포트폴리오 조회
        List<EventPortfolios> portfolios = eventPortfoliosRepository.findByEventId(goalId);

        // productType 기준으로 Map 변환
        Map<String, EventPortfolios> portfolioMap = portfolios.stream()
                .collect(Collectors.toMap(
                        gp -> gp.getProductType().name(),
                        gp -> gp));

        // 각 타입별 비율 + 계좌 업데이트
        updatePortfolio(portfolioMap.get("STOCK"),
                request.getStockRatio(), request.getStockAssetId());

        updatePortfolio(portfolioMap.get("BOND"),
                request.getBondRatio(), request.getBondAssetId());

        updatePortfolio(portfolioMap.get("DEPOSIT"),
                request.getCashRatio(), request.getDepositAssetId());

        return getPortfolios(userId, goalId);
    }

    private void updatePortfolio(EventPortfolios portfolio,
                                 Integer ratio, UUID assetId) {
        if (portfolio == null) return;

        // 비율 업데이트
        portfolio.updateRatio(ratio);

        // 계좌 변경 요청이 있을 때만 업데이트 (null이면 기존 유지)
        if (assetId != null) {
            Assets asset = assetRepository.findById(assetId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "계좌를 찾을 수 없습니다: " + assetId));
            portfolio.linkAsset(asset);
        }
    }
}