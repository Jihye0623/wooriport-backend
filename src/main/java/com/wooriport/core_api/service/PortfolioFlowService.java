package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.portfolioFlow.AvailableAssetListResponseDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto.FlowDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto.GatheringAssetDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto.ProductItemDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowListResponseDto.SourceItemDto;
import com.wooriport.core_api.base.dto.portfolioFlow.PortfolioFlowUpdateRequestDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.PortfolioFlowItems;
import com.wooriport.core_api.domain.PortfolioFlows;
import com.wooriport.core_api.domain.Products;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.PortfolioFlowItemRepository;
import com.wooriport.core_api.repository.PortfolioFlowRepository;
import com.wooriport.core_api.repository.ProductRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final UserRepository userRepository;

    // PATCH /portfolio-flows/{flowId}
    // gathering + 상품(PUT) 교체. amount/추천통장정보/AI 코멘트는 보존
    @Transactional
    public PortfolioFlowListResponseDto.FlowDto updateFlow(
            UUID userId, UUID flowId, PortfolioFlowUpdateRequestDto request) {

        // 1. 소유권 검증
        PortfolioFlows flow = portfolioFlowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("흐름을 찾을 수 없습니다: " + flowId));
        if (!flow.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 흐름에 대한 권한이 없습니다.");
        }

        // 2. amount — 보내준 경우에만 갱신 (없으면 AI 설정값 유지)
        if (request.getAmount() != null) {
            flow.updateAmount(request.getAmount());
        }

        // 3. gathering 처리
        //    - 보유 계좌 선택(id 있음): 해당 계좌 연결
        //    - id 없음 + 아직 추천 상태(FK null + 추천정보 있음): 추천 계좌를 assets 에 개설 후 연결
        //    - 그 외(이미 보유계좌 연결됨): 유지
        if (request.getGatheringAssetId() != null) {
            Assets picked = assetRepository.findByIdAndUserId(request.getGatheringAssetId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "모으기 통장을 찾을 수 없습니다: " + request.getGatheringAssetId()));
            flow.linkGatheringAsset(picked);
        } else if (flow.getGatheringAsset() == null && flow.getGatheringName() != null) {
            Assets opened = openRecommendedAccount(flow);
            flow.linkGatheringAsset(opened);
        }

        // 4. 기존 상품 AI 코멘트 보존 (productId 매칭) — 재INSERT 시 이어붙이기 위함
        Map<UUID, String> commentByProduct = flow.getItems().stream()
                .filter(i -> i.getProduct() != null && i.getAiComment() != null)
                .collect(Collectors.toMap(
                        i -> i.getProduct().getId(),
                        PortfolioFlowItems::getAiComment,
                        (a, b) -> a));

        // 5. 기존 items 삭제 후 상품(PUT) 재INSERT
        portfolioFlowItemRepository.deleteByFlowId(flowId);
        portfolioFlowItemRepository.flush();  // DELETE 가 INSERT 보다 먼저 수행되도록 보장

        List<PortfolioFlowItems> newItems = new ArrayList<>();
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
                String aiComment = p != null ? commentByProduct.get(p.getId()) : null;
                newItems.add(PortfolioFlowItems.builder()
                        .flow(flow)
                        .asset(a)
                        .product(p)
                        .productRatio(prod.getProductRatio())
                        .aiComment(aiComment)
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

    // 추천 통장(gathering_*) 정보로 assets 에 실제 계좌를 개설 (개설 시점 잔액 0)
    private Assets openRecommendedAccount(PortfolioFlows flow) {
        Assets asset = Assets.builder()
                .user(flow.getUser())
                .institution(flow.getGatheringInstitution() != null ? flow.getGatheringInstitution() : "")
                .accountName(flow.getGatheringName())
                .assetType(parseAccountType(flow.getGatheringType()))
                .balance(0L)
                .bankType(resolveBankType(flow.getGatheringInstitution()))
                .syncedAt(LocalDateTime.now())
                .isSalary(false)
                .build();
        Assets saved = assetRepository.save(asset);
        log.info("[PortfolioFlowService] 추천 계좌 개설 — userId={}, flowId={}, assetId={}, name={}",
                flow.getUser().getId(), flow.getId(), saved.getId(), saved.getAccountName());
        return saved;
    }

    private Assets.AccountType parseAccountType(String type) {
        if (type == null) return null;
        try {
            return Assets.AccountType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Assets.BankType resolveBankType(String institution) {
        return institution != null && institution.contains("우리")
                ? Assets.BankType.WOORI : Assets.BankType.OTHER;
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
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        List<PortfolioFlows> flows = portfolioFlowRepository.findAllByUserIdWithDetails(userId);

        List<FlowDto> flowDtos = flows.stream()
                .sorted(Comparator
                        .comparing((PortfolioFlows f) -> f.getEvent() != null) // 기본(NULL) 먼저
                        .thenComparing(PortfolioFlows::getCreatedAt))
                .map(this::toFlowDto)
                .collect(Collectors.toList());

        return PortfolioFlowListResponseDto.builder()
                .monthlyInvestAmount(user.getMonthlyInvestAmount())
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
                .amount(flow.getAmount())
                .isActive(flow.getIsActive())
                .isRecommendation(flow.getGatheringAsset() == null)  // 모을 통장 없음 = 계좌 추천
                .accountComment(flow.getAccountComment())
                .expectedRrPct(flow.getExpectedRrPct())
                .investmentMonths(flow.getInvestmentMonths())
                .expectedAmount(flow.getExpectedAmount())
                .rrComment(flow.getRrComment())
                .gatheringAsset(toGatheringDto(flow))
                .sources(sources)
                .products(products)
                .build();
    }

    // 보유 계좌 선택이면 계좌에서, 계좌 추천이면 flow 에 저장된 gathering_* 에서 구성
    private GatheringAssetDto toGatheringDto(PortfolioFlows flow) {
        Assets asset = flow.getGatheringAsset();
        if (asset != null) {
            // 보유 계좌 선택
            return GatheringAssetDto.builder()
                    .id(asset.getId())
                    .institution(asset.getInstitution())
                    .accountName(asset.getAccountName())
                    .assetNumber(asset.getAssetNumber())
                    .assetType(asset.getAssetType() != null ? asset.getAssetType().name() : null)
                    .balance(asset.getBalance())
                    .build();
        }
        if (flow.getGatheringName() == null) return null;  // gathering 정보 자체가 없음
        // 계좌 추천
        return GatheringAssetDto.builder()
                .institution(flow.getGatheringInstitution())
                .accountName(flow.getGatheringName())
                .assetType(flow.getGatheringType())
                .interestRate(flow.getGatheringInterestRate())
                .build();
    }

    private SourceItemDto toSourceDto(PortfolioFlowItems item) {
        Assets a = item.getAsset();
        return SourceItemDto.builder()
                .id(item.getId())
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
                .productType(p != null && p.getProductType() != null ? p.getProductType().name() : null)
                .productId(p != null ? p.getId() : null)
                .productName(p != null ? p.getName() : null)
                .productInstitution(p != null ? p.getInstitution() : null)
                .interestRate(p != null ? p.getInterestRate() : null)
                .comment(item.getAiComment())
                .build();
    }
}
