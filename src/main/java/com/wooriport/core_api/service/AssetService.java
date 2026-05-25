package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.asset.*;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.DummyMydata;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.DummyMydataRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final DummyMydataRepository dummyMydataRepository;

    @Transactional(readOnly = true)
    public MydataPreviewResponseDto previewMydata(UUID userId, List<String> institutions) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<DummyMydata> dummyList = dummyMydataRepository.findByEmail(user.getEmail());

        if (dummyList.isEmpty()) {
            throw new IllegalStateException("연동 가능한 계좌 데이터가 없습니다: " + user.getEmail());
        }

        List<MydataPreviewResponseDto.MydataItem> items = dummyList.stream()
                .filter(d -> institutions == null
                        || institutions.isEmpty()
                        || institutions.contains(d.getInstitution()))  // ← 여기서 바로 필터
                .map(d -> MydataPreviewResponseDto.MydataItem.builder()
                        .assetNumber(d.getAssetNumber())
                        .institution(d.getInstitution())
                        .assetType(d.getAssetType().name())
                        .accountName(d.getAccountName())
                        .accountPurpose(d.getAccountPurpose())
                        .balance(d.getBalance())
                        .bankType(d.getBankType().name())
                        .isSalary(d.getIsSalary())
                        .build())
                .toList();

        return MydataPreviewResponseDto.builder()
                .accounts(items)
                .totalCount(items.size())
                .build();
    }

    // 2. 선택 연동
    @Transactional
    public AssetListResponseDto syncAssets(UUID userId, AssetSyncRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 기존 계좌 soft delete
        assetRepository.findByUserIdAndDeletedAtIsNull(userId).forEach(Assets::delete);

        // 이메일 기준 더미 계좌 전체 조회
        List<DummyMydata> dummyList = dummyMydataRepository.findByEmail(user.getEmail());

        if (dummyList.isEmpty()) {
            throw new IllegalStateException("연동 가능한 계좌 데이터가 없습니다: " + user.getEmail());
        }

        // 선택한 계좌번호로 필터링
        // selectedAssetNumbers가 null이거나 비어있으면 전체 연동
        List<DummyMydata> selectedList;
        if (request == null
                || request.getSelectedAssetNumbers() == null
                || request.getSelectedAssetNumbers().isEmpty()) {
            selectedList = dummyList;  // 전체 연동
        } else {
            selectedList = dummyList.stream()
                    .filter(d -> request.getSelectedAssetNumbers()
                            .contains(d.getAssetNumber()))
                    .toList();

            if (selectedList.isEmpty()) {
                throw new IllegalArgumentException("선택한 계좌를 찾을 수 없습니다.");
            }
        }

        // assets 저장 (is_salary = false 초기화)
        List<Assets> newAssets = selectedList.stream()
                .map(d -> Assets.builder()
                        .user(user)
                        .institution(d.getInstitution())
                        .assetType(d.getAssetType())
                        .accountName(d.getAccountName())
                        .accountPurpose(d.getAccountPurpose())
                        .assetNumber(d.getAssetNumber())
                        .balance(d.getBalance())
                        .isSalary(false)          // sync 시 전부 false
                        .bankType(d.getBankType())
                        .syncedAt(LocalDateTime.now())
                        .build())
                .toList();

        assetRepository.saveAll(newAssets);

        log.info("[AssetService] 마이데이터 연동 완료 — email: {}, 전체: {}개 중 {}개 선택",
                user.getEmail(), dummyList.size(), newAssets.size());

        return toListResponse(newAssets);
    }

    // POST /assets/sync
    // 더미 계좌 연동 (마이데이터 대체)
    @Transactional
    public AssetListResponseDto syncAssets(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 이미 연동된 계좌 있으면 그 더미데이터 삭제
        List<Assets> existing = assetRepository.findByUserIdAndDeletedAtIsNull(userId);
        if (!existing.isEmpty()) {
            existing.forEach(Assets::delete);  // deleted_at = NOW()
        }

        // 사용자 이메일과 일치하는 더미 데이터만 조회
        List<DummyMydata> dummyData = dummyMydataRepository.findByEmail(user.getEmail());

        if (dummyData.isEmpty()) {
            throw new IllegalStateException(
                    "해당 이메일의 더미 마이데이터가 없습니다: " + user.getEmail());
        }

        // dummy_mydata → assets 변환 후 저장
        List<Assets> newAssets = dummyData.stream()
                .map(d -> Assets.builder()
                        .user(user)
                        .institution(d.getInstitution())
                        .assetType(d.getAssetType())
                        .accountName(d.getAccountName())
                        .accountPurpose(d.getAccountPurpose())
                        .assetNumber(d.getAssetNumber())
                        .balance(d.getBalance())
                        .isSalary(false)
                        .syncedAt(LocalDateTime.now())
                        .bankType(d.getBankType())
                        .build())
                .collect(Collectors.toList());

        assetRepository.saveAll(newAssets);
        log.info("마이데이터 연동 완료 — email: {}, 계좌 {}개", user.getEmail(), newAssets.size());

        return toListResponse(newAssets);
    }


    // GET /assets
    // 전체 자산 목록 조회
    @Transactional(readOnly = true)
    public AssetListResponseDto getAssets(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);

        if (assets.isEmpty()) {
            throw new IllegalStateException("연동된 계좌가 없습니다. 먼저 계좌를 연동해주세요.");
        }

        return toListResponse(assets);
    }

    // ──────────────────────────────────────
    // PATCH /assets/{assetId}/salary
    // 급여통장 설정
    // 1. 기존 급여통장 해제
    // 2. 선택한 계좌 급여통장으로 설정
    // 3. isWooriBank 반환 → 프론트 분기용
    // ──────────────────────────────────────
    @Transactional
    public SalarySettingResponseDto setSalaryAccount(UUID userId, UUID assetId) {

        // 기존 급여통장 해제
        assetRepository.findByUserIdAndIsSalaryTrueAndDeletedAtIsNull(userId)
                .ifPresent(Assets::unmarkAsSalary);

        // 선택한 계좌 급여통장 설정
        Assets asset = assetRepository.findByIdAndUserId(assetId, userId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));

        asset.markAsSalary();

        if (asset.isWooriBank()) {
            Users user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            user.connectAutoTransfer(asset.getId());
        }

        return SalarySettingResponseDto.builder()
                .assetId(asset.getId())
                .institution(asset.getInstitution())
                .isWooriBank(asset.isWooriBank())  // 프론트 분기용
                .build();
    }

    // POST /assets/auto-transfer/connect
    // 타행 급여 계좌 → 우리은행 자동이체 연결
    // users 테이블 수정 없음 — assets만 사용
    @Transactional
    public void connectAutoTransfer(UUID userId, AutoTransferConnectRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        Assets fromAsset = assetRepository
                .findByUserIdAndIsSalaryTrueAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("급여통장을 먼저 설정해주세요."));

        Assets toAsset = assetRepository.findByIdAndUserId(request.getToAssetId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("입금 계좌를 찾을 수 없습니다."));

        if (toAsset.getBankType() != Assets.BankType.WOORI) {
            throw new IllegalArgumentException("자동이체 연결은 우리은행 계좌만 가능합니다.");
        }

        user.connectAutoTransfer(toAsset.getId());
        user.updateSalaryDate(request.getSalaryDate());

        log.info("[AssetService] 자동이체 연결 — 타행: {}, 우리은행: {}, 급여일: {}",
                fromAsset.getInstitution(), toAsset.getInstitution(), request.getSalaryDate());
    }

    // GET /assets/auto-transfer/status
    // 자동이체 연결 여부 조회
    // SALARY 계좌 존재 여부로 판단
    @Transactional(readOnly = true)
    public AutoTransferStatusResponseDto getAutoTransferStatus(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        Optional<Assets> salaryAsset = assetRepository
                .findByUserIdAndIsSalaryTrue(userId);

        // SALARY 계좌 없음 → 자동이체 미연결
        if (salaryAsset.isEmpty()) {
            return AutoTransferStatusResponseDto.builder()
                    .isConnected(false)
                    .restrictedFeatures(List.of(
                            "AI 기반 급여 자동 분배",
                            "월급 입금 감지 리밸런싱",
                            "자동 이체 실행"))
                    .build();
        }

        // SALARY 계좌 있음 + WOORI 계좌 존재 → 정상 연결
        boolean hasWooriAccount = assetRepository
                .findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .anyMatch(a -> a.getBankType() == Assets.BankType.WOORI);

        return AutoTransferStatusResponseDto.builder()
                .isConnected(hasWooriAccount)
                .restrictedFeatures(hasWooriAccount
                        ? List.of()
                        : List.of("AI 기반 급여 자동 분배", "자동 이체 실행"))
                .fromAssetId(salaryAsset.get().getId())
                .fromInstitution(salaryAsset.get().getInstitution())
                .build();
    }

    // PATCH /transfer-plans/scheduled-date
    // 자동이체 실행일 설정 → users.salary_date 저장
    @Transactional
    public void updateScheduledDate(UUID userId, ScheduledDateRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        user.updateSalaryDate(request.getScheduledDate());
    }

    // GET /assets/summary
    // 총 자산 요약
    @Transactional(readOnly = true)
    public AssetSummaryResponseDto getAssetSummary(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        List<Assets> assets = assetRepository.findByUserIdAndDeletedAtIsNull(userId);

        long totalBalance = assets.stream()
                .mapToLong(Assets::getBalance)
                .sum();

        return AssetSummaryResponseDto.builder()
                .totalBalance(totalBalance)
                .assetCount(assets.size())
                .build();
    }

    // Entity → Response 변환
    private AssetListResponseDto toListResponse(List<Assets> assets) {
        long totalBalance = assets.stream().mapToLong(Assets::getBalance).sum();

        List<AssetListResponseDto.AssetItem> items = assets.stream()
                .map(a -> AssetListResponseDto.AssetItem.builder()
                        .id(a.getId())
                        .institution(a.getInstitution())
                        .assetType(a.getAssetType().name())
                        .assetNumber(a.getAssetNumber())
                        .accountName(a.getAccountName())
                        .accountPurpose(a.getAccountPurpose())
                        .isSalary(a.getIsSalary())
                        .balance(a.getBalance())
                        .bankType(a.getBankType().name())
                        .syncedAt(a.getSyncedAt().toString())
                        .build())
                .collect(Collectors.toList());

        return AssetListResponseDto.builder()
                .assets(items)
                .totalCount(items.size())
                .build();
    }
}