package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.challenge.ChallengeCreateRequestDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.domain.Notifications;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.MiniChallengesRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
    private final ChallengeAgentService challengeAgentService;
    private final NotificationService notificationService;

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
                .challengeType(request.getChallengeType())
                .target(request.getTarget())
                .estimatedSaving(request.getEstimatedSaving())
                .rewardStockTicker(request.getTicker())
                .status(MiniChallenges.ChallengeStatus.IN_PROGRESS)
                .build();

        challenge.start();
        miniChallengesRepository.save(challenge);

        challengeRedisService.save(userId, challenge);

        return challenge.getId();
    }

    // 거래 발생 시 진행 업데이트 (TransactionConsumer에서 호출)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID userId, String category, long amount) {
        try {
        if (!challengeRedisService.exists(userId)) return;

        Map<Object, Object> cache = challengeRedisService.get(userId);
        if (!category.equals(cache.get("category"))) return;

        MiniChallenges.ChallengeType challengeType = MiniChallenges.ChallengeType.valueOf((String) cache.get("challengeType"));
        long target           = Long.parseLong((String) cache.get("target"));
        int notifiedThreshold = Integer.parseInt((String) cache.get("notifiedThreshold"));

        long delta = challengeType == MiniChallenges.ChallengeType.AMOUNT ? amount : 1L;
        challengeRedisService.increment(userId, delta);
        long current = Long.parseLong((String) cache.get("currentValue")) + delta;

        int progress = target > 0 ? (int) (current * 100 / target) : 0;

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
        } catch (Exception e) {
            log.error("[Challenge] 진행 업데이트 실패 — userId: {}, 사유: {}", userId, e.getMessage());
        }
    }

    // 알림 발송 + DB 동기화
    private void sendThresholdNotification(UUID userId, String challengeId, int threshold) {
        syncToDb(userId, UUID.fromString(challengeId));
        miniChallengesRepository.findById(UUID.fromString(challengeId)).ifPresent(challenge -> {
            try {
                String nagMessage = challengeAgentService.nag(userId, challenge, threshold).getNagMessage();
                notificationService.saveAndSend(
                        userId,
                        Notifications.NotificationType.CHALLENGE_NAG,
                        "챌린지 " + threshold + "% 소비!",
                        nagMessage
                );
            } catch (Exception e) {
                log.warn("[Challenge] nag 알림 실패 — userId: {}, threshold: {}%, 사유: {}", userId, threshold, e.getMessage());
            }
        });
    }

    // Redis → DB 동기화
    @Transactional
    public void syncToDb(UUID userId, UUID challengeId) {
        Map<Object, Object> cache = challengeRedisService.get(userId);
        if (cache.isEmpty()) return;

        miniChallengesRepository.findById(challengeId).ifPresent(challenge -> {
            long current           = Long.parseLong((String) cache.getOrDefault("currentValue", "0"));
            int  notifiedThreshold = Integer.parseInt((String) cache.getOrDefault("notifiedThreshold", "0"));
            challenge.syncProgress(current, notifiedThreshold);
        });
    }
}
