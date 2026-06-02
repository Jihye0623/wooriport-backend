package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.challenge.ChallengeCreateRequestDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.MiniChallengesRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final MiniChallengesRepository miniChallengesRepository;
    private final UserRepository userRepository;
    private final ChallengeRedisService challengeRedisService;

    private static final List<Integer> THRESHOLDS = List.of(50, 80, 90);

    // POST /challenges — 승인된 챌린지 저장 + Redis 등록
    @Transactional
    public UUID create(UUID userId, ChallengeCreateRequestDto request) {
        Users user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        MiniChallenges challenge = MiniChallenges.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .targetAmount(request.getTargetAmount())
                .targetCount(request.getTargetCount())
                .rewardStockTicker(request.getRewardStockTicker())
                .status(MiniChallenges.ChallengeStatus.IN_PROGRESS)
                .build();

        challenge.start();
        miniChallengesRepository.save(challenge);

        challengeRedisService.save(userId, challenge);

        return challenge.getId();
    }

    // 거래 발생 시 진행 업데이트 (TransactionConsumer에서 호출)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID userId, String category, long amount) {
        if (!challengeRedisService.exists(userId)) return;

        Map<Object, Object> cache = challengeRedisService.get(userId);
        if (!category.equals(cache.get("category"))) return;

        String targetAmountStr = (String) cache.get("targetAmount");
        String targetCountStr  = (String) cache.get("targetCount");
        int notifiedThreshold  = Integer.parseInt((String) cache.get("notifiedThreshold"));

        int progress = 0;

        if (targetAmountStr != null && !targetAmountStr.isBlank()) {
            // 금액 기반
            challengeRedisService.incrementAmount(userId, amount);
            long currentAmount = Long.parseLong((String) cache.get("currentAmount")) + amount;
            long targetAmount  = Long.parseLong(targetAmountStr);
            if (targetAmount > 0) progress = (int) (currentAmount * 100 / targetAmount);

        } else if (targetCountStr != null && !targetCountStr.isBlank()) {
            // 횟수 기반 — 거래 1건 = 1회
            challengeRedisService.incrementCount(userId, 1);
            long currentCount = Long.parseLong((String) cache.getOrDefault("currentCount", "0")) + 1;
            long targetCount  = Long.parseLong(targetCountStr);
            if (targetCount > 0) progress = (int) (currentCount * 100 / targetCount);
        }

        for (int threshold : THRESHOLDS) {
            if (progress >= threshold && notifiedThreshold < threshold) {
                challengeRedisService.updateNotifiedThreshold(userId, threshold);
                notifiedThreshold = threshold;
                sendThresholdNotification(userId, cache.get("id").toString(), threshold);
            }
        }

        if (progress >= 100) {
            UUID challengeId = UUID.fromString(cache.get("id").toString());
            syncToDb(userId, challengeId);
            miniChallengesRepository.findById(challengeId).ifPresent(MiniChallenges::fail);
            challengeRedisService.delete(userId);
            log.info("[Challenge] 한도 초과 즉시 실패 — userId: {}, challengeId: {}", userId, challengeId);
        }
    }

    // 알림 발송 + DB 동기화
    private void sendThresholdNotification(UUID userId, String challengeId, int threshold) {
        log.info("[Challenge] 임계값 알림 — userId: {}, challengeId: {}, threshold: {}%", userId, challengeId, threshold);
        syncToDb(userId, UUID.fromString(challengeId));
        // TODO: FastAPI 호출 → 멘트 받기 → 알림 발송
    }

    // Redis → DB 동기화
    @Transactional
    public void syncToDb(UUID userId, UUID challengeId) {
        Map<Object, Object> cache = challengeRedisService.get(userId);
        if (cache.isEmpty()) return;

        miniChallengesRepository.findById(challengeId).ifPresent(challenge -> {
            long currentAmount     = Long.parseLong((String) cache.getOrDefault("currentAmount", "0"));
            int  currentCount      = (int) Long.parseLong((String) cache.getOrDefault("currentCount", "0"));
            int  notifiedThreshold = Integer.parseInt((String) cache.getOrDefault("notifiedThreshold", "0"));
            challenge.syncProgress(currentAmount, currentCount, notifiedThreshold);
        });
    }
}
