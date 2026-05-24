package com.wooriport.core_api.service;


import com.wooriport.core_api.base.dto.portfolioItems.PortfolioItemListResponseDto;
import com.wooriport.core_api.base.dto.portfolioItems.PortfolioItemUpdateRequestDto;
import com.wooriport.core_api.base.dto.portfolioItems.PortfolioListResponseDto;
import com.wooriport.core_api.base.dto.portfolioItems.PortfolioUpdateRequestDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Event;
import com.wooriport.core_api.domain.PortfolioItems;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioItemRepository;
import com.wooriport.core_api.repository.EventRepository;
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
public class PortfolioItemService {

    private final PortfolioItemRepository portfolioItemRepository;
    private final AssetRepository assetRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    // ──────────────────────────────────────
    // GET /event/portfolios
    // 기본 포트폴리오 조회 (event_id = null, 최신)
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public PortfolioItemListResponseDto getBasePortfolios(UUID userId) {
        List<PortfolioItems> items = portfolioItemRepository
                .findByUserIdAndEventIsNullOrderByCreatedAtDesc(userId);

        return toResponse(items, null);
    }

    // ──────────────────────────────────────
    // GET /event/{eventId}/portfolios
    // 이벤트 포트폴리오 조회
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public PortfolioItemListResponseDto getEventPortfolios(UUID userId, UUID eventId) {
        // 본인 이벤트인지 검증
        eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다."));

        List<PortfolioItems> items = portfolioItemRepository
                .findByUserIdAndEventId(userId, eventId);

        return toResponse(items, eventId);
    }

    // PATCH /portfolios-items (기본 포트폴리오 수정)
    @Transactional
    public PortfolioItemListResponseDto updateBasePortfolios(
            UUID userId, PortfolioItemUpdateRequestDto request) {

        // 비율 합계 검증
        int totalRatio = request.getItems().stream()
                .mapToInt(PortfolioItemUpdateRequestDto.ItemDto::getProductRatio)
                .sum();

        if (totalRatio != 100) {
            throw new IllegalArgumentException(
                    "비율 합계가 100이어야 합니다. 현재: " + totalRatio);
        }

        // 기존 기본 포트폴리오 삭제 후 재생성
        portfolioItemRepository.deleteByUserIdAndEventIsNull(userId);

        List<PortfolioItems> saved = request.getItems().stream()
                .map(item -> {
                    Assets asset = null;
                    if (item.getAssetId() != null) {
                        asset = assetRepository.findById(item.getAssetId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "계좌를 찾을 수 없습니다: " + item.getAssetId()));
                    }

                    return PortfolioItems.builder()
                            .user(userRepository.getReferenceById(userId))
                            .event(null)   // 기본 포트폴리오
                            .productType(PortfolioItems.ProductType.valueOf(item.getProductType()))
                            .productRatio(item.getProductRatio())
                            .asset(asset)
                            .build();
                })
                .collect(Collectors.toList());

        portfolioItemRepository.saveAll(saved);

        log.info("[PortfolioItemService] 기본 포트폴리오 수정 — userId: {}, {}개",
                userId, saved.size());

        return toResponse(saved, null);
    }

    // ──────────────────────────────────────
    // PATCH /event/{eventId}/portfolios
    // 이벤트 포트폴리오 수정
    // ──────────────────────────────────────
    @Transactional
    public PortfolioItemListResponseDto updateEventPortfolios(
            UUID userId, UUID eventId, PortfolioItemUpdateRequestDto request) {

        // 비율 합계 검증
        int totalRatio = request.getItems().stream()
                .mapToInt(PortfolioItemUpdateRequestDto.ItemDto::getProductRatio)
                .sum();

        if (totalRatio != 100) {
            throw new IllegalArgumentException(
                    "비율 합계가 100이어야 합니다. 현재: " + totalRatio);
        }

        // 본인 이벤트 검증
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다."));

        // 기존 이벤트 포트폴리오 삭제
        portfolioItemRepository.deleteByUserIdAndEventId(userId, eventId);

        // 새로 저장
        List<PortfolioItems> saved = request.getItems().stream()
                .map(item -> {
                    Assets asset = null;
                    if (item.getAssetId() != null) {
                        asset = assetRepository.findById(item.getAssetId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "계좌를 찾을 수 없습니다: " + item.getAssetId()));
                    }

                    return PortfolioItems.builder()
                            .user(userRepository.getReferenceById(userId))
                            .event(event)
                            .productType(PortfolioItems.ProductType.valueOf(item.getProductType()))
                            .productRatio(item.getProductRatio())
                            .asset(asset)
                            .build();
                })
                .collect(Collectors.toList());

        portfolioItemRepository.saveAll(saved);

        log.info("[PortfolioItemService] 이벤트 포트폴리오 수정 — userId: {}, eventId: {}, {}개",
                userId, eventId, saved.size());

        return toResponse(saved, eventId);
    }

    // ──────────────────────────────────────
    // 공통 변환
    // ──────────────────────────────────────
    private PortfolioItemListResponseDto toResponse(List<PortfolioItems> items, UUID eventId) {
        int totalRatio = items.stream()
                .mapToInt(PortfolioItems::getProductRatio)
                .sum();

        List<PortfolioItemListResponseDto.ItemDto> dtos = items.stream()
                .map(p -> PortfolioItemListResponseDto.ItemDto.builder()
                        .id(p.getId())
                        .productType(p.getProductType().name())  // STOCK / BOND / DEPOSIT
                        .productRatio(p.getProductRatio())
                        .isLinked(p.getAsset() != null)
                        .institution(p.getAsset() != null ? p.getAsset().getInstitution() : null)
                        .assetNumber(p.getAsset() != null ? p.getAsset().getAssetNumber() : null)
                        .balance(p.getAsset() != null ? p.getAsset().getBalance() : null)
                        .build())
                .collect(Collectors.toList());

        return PortfolioItemListResponseDto.builder()
                .items(dtos)
                .totalRatio(totalRatio)
                .eventId(eventId)  // null = 기본 포트폴리오
                .build();
    }
}