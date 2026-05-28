package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.portfolioFlow.AvailableAssetListResponseDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto.FlowDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto.GatheringAssetDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto.ProductItemDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto.SourceItemDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowUpdateRequestDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.PortfolioFlowItems;
import com.wooriport.core_api.domain.PortfolioFlows;
import com.wooriport.core_api.domain.Products;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioFlowItemRepository;
import com.wooriport.core_api.repository.PortfolioFlowRepository;
import com.wooriport.core_api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioFlowService {

    private final PortfolioFlowRepository portfolioFlowRepository;
    private final PortfolioFlowItemRepository portfolioFlowItemRepository;
    private final AssetRepository assetRepository;
    private final ProductRepository productRepository;

    // PATCH /portfolio-flows/{flowId}
    // gathering_asset_id + items(PULL/PUT) 전체 교체
    @Transactional
    public PortfolioFlowListResponseDto.FlowDto updateFlow(
            UUID userId, UUID flowId, PortfolioFlowUpdateRequestDto request) {

        // 1. 소유권 검증
        PortfolioFlows flow = portfolioFlowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("흐름을 찾을 수 없습니다: " + flowId));
        if (!flow.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 흐름에 대한 권한이 없습니다.");
        }

        // 2. gathering 갱신
        Assets newGathering = null;
        if (request.getGatheringAssetId() != null) {
            newGathering = assetRepository.findByIdAndUserId(request.getGatheringAssetId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "모으기 통장을 찾을 수 없습니다: " + request.getGatheringAssetId()));
        }
        flow.updateGatheringAsset(newGathering);

        // 3. 기존 items 삭제 후 재INSERT (PULL → PUT 순)
        portfolioFlowItemRepository.deleteByFlowId(flowId);
        // flush 로 DELETE 가 INSERT 보다 먼저 수행되도록 보장
        portfolioFlowItemRepository.flush();

        List<PortfolioFlowItems> newItems = new ArrayList<>();

        if (request.getSources() != null) {
            for (var src : request.getSources()) {
                Assets a = assetRepository.findByIdAndUserId(src.getAssetId(), userId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "끌어오기 통장을 찾을 수 없습니다: " + src.getAssetId()));
                newItems.add(PortfolioFlowItems.builder()
                        .flow(flow)
                        .stepType(PortfolioFlowItems.StepType.PULL)
                        .asset(a)
                        .amount(src.getAmount())
                        .build());
            }
        }

        if (request.getProducts() != null) {
            for (var prod : request.getProducts()) {
                Products p = null;
                if (prod.getProductId() != null) {
                    p = productRepository.findById(prod.getProductId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "상품을 찾을 수 없습니다: " + prod.getProductId()));
                }
                Assets a = null;
                if (prod.getAssetId() != null) {
                    a = assetRepository.findByIdAndUserId(prod.getAssetId(), userId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "넣기 자산을 찾을 수 없습니다: " + prod.getAssetId()));
                }
                newItems.add(PortfolioFlowItems.builder()
                        .flow(flow)
                        .stepType(PortfolioFlowItems.StepType.PUT)
                        .asset(a)
                        .product(p)
                        .productType(prod.getProductType())
                        .productRatio(prod.getProductRatio())
                        .build());
            }
        }

        List<PortfolioFlowItems> saved = portfolioFlowItemRepository.saveAll(newItems);

        // "관리 시작하기" — 최초 활성화 시점에만 started_at 기록
        if (flow.getStartedAt() == null) {
            flow.activate();
        }

        log.info("[PortfolioFlowService] 흐름 수정 — userId={}, flowId={}, items={}, startedAt={}",
                userId, flowId, saved.size(), flow.getStartedAt());

        // 4. 갱신된 flow 를 FlowDto 로 반환
        //    JPA 영속 컨텍스트의 flow 에 items 관계가 자동 동기화되지 않을 수 있어
        //    명시적으로 saved 리스트로 toFlowDto 를 만들 수도 있지만
        //    fetch join 으로 새로 조회해 N+1 도 함께 정리
        PortfolioFlows refreshed = portfolioFlowRepository.findAllByUserIdWithDetails(userId).stream()
                .filter(f -> f.getId().equals(flowId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("갱신된 흐름 조회 실패: " + flowId));

        return toFlowDto(refreshed);
    }

    // 끌어오기/모으기 통장 후보 — portfolios.asset_id 와 모든 흐름의 gathering_asset_id 제외
    @Transactional(readOnly = true)
    public AvailableAssetListResponseDto getAvailableAssets(UUID userId) {
        List<AvailableAssetListResponseDto.AssetDto> assets = assetRepository
                .findAvailableForFlows(userId).stream()
                .map(a -> AvailableAssetListResponseDto.AssetDto.builder()
                        .id(a.getId())
                        .institution(a.getInstitution())
                        .accountName(a.getAccountName())
                        .assetNumber(a.getAssetNumber())
                        .assetType(a.getAssetType() != null ? a.getAssetType().name() : null)
                        .balance(a.getBalance())
                        .build())
                .collect(Collectors.toList());

        return AvailableAssetListResponseDto.builder()
                .assets(assets)
                .build();
    }

    @Transactional(readOnly = true)
    public PortfolioFlowListResponseDto getFlows(UUID userId) {
        List<PortfolioFlows> flows = portfolioFlowRepository.findAllByUserIdWithDetails(userId);

        List<FlowDto> flowDtos = flows.stream()
                .sorted(Comparator
                        .comparing((PortfolioFlows f) -> f.getEvent() != null) // 기본(NULL) 먼저
                        .thenComparing(PortfolioFlows::getCreatedAt))
                .map(this::toFlowDto)
                .collect(Collectors.toList());

        return PortfolioFlowListResponseDto.builder()
                .flows(flowDtos)
                .build();
    }

    private FlowDto toFlowDto(PortfolioFlows flow) {
        List<SourceItemDto> sources = flow.getItems().stream()
                .filter(PortfolioFlowItems::isPull)
                .map(this::toSourceDto)
                .collect(Collectors.toList());

        List<ProductItemDto> products = flow.getItems().stream()
                .filter(PortfolioFlowItems::isPut)
                .map(this::toProductDto)
                .collect(Collectors.toList());

        return FlowDto.builder()
                .id(flow.getId())
                .eventId(flow.getEvent() != null ? flow.getEvent().getId() : null)
                .title(flow.getTitle())
                .summary(flow.getSummary())
                .term(flow.getTerm())
                .isActive(flow.getIsActive())
                .gatheringAsset(toGatheringDto(flow.getGatheringAsset()))
                .sources(sources)
                .products(products)
                .build();
    }

    private GatheringAssetDto toGatheringDto(Assets asset) {
        if (asset == null) return null;
        return GatheringAssetDto.builder()
                .id(asset.getId())
                .institution(asset.getInstitution())
                .accountName(asset.getAccountName())
                .assetNumber(asset.getAssetNumber())
                .assetType(asset.getAssetType() != null ? asset.getAssetType().name() : null)
                .balance(asset.getBalance())
                .build();
    }

    private SourceItemDto toSourceDto(PortfolioFlowItems item) {
        Assets a = item.getAsset();
        return SourceItemDto.builder()
                .id(item.getId())
                .amount(item.getAmount())
                .assetId(a != null ? a.getId() : null)
                .institution(a != null ? a.getInstitution() : null)
                .accountName(a != null ? a.getAccountName() : null)
                .assetNumber(a != null ? a.getAssetNumber() : null)
                .assetType(a != null && a.getAssetType() != null ? a.getAssetType().name() : null)
                .build();
    }

    private ProductItemDto toProductDto(PortfolioFlowItems item) {
        Products p = item.getProduct();
        return ProductItemDto.builder()
                .id(item.getId())
                .productRatio(item.getProductRatio())
                .productType(item.getProductType())
                .productId(p != null ? p.getId() : null)
                .productName(p != null ? p.getName() : null)
                .productInstitution(p != null ? p.getInstitution() : null)
                .interestRate(p != null ? p.getInterestRate() : null)
                .build();
    }
}
