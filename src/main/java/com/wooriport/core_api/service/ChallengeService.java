package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.challenge.ChallengeActiveResponseDto;
import com.wooriport.core_api.base.dto.challenge.ChallengeCreateRequestDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.domain.Notifications;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.MiniChallengesRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> DELIVERY_SENDERS  = Set.of("우아한형제들", "요기요", "쿠팡이츠");
    private static final Set<String> ALCOHOL_KEYWORDS  = Set.of("바", "주점", "호프");
    private static final Set<String> SHOPPING_SENDERS  = Set.of("29CM","지그재그","에이블리","올리브영","무신사","나이키","자라");

    // POST /challenges — 승인된 챌린지 저장 + Redis 등록
    @Transactional
    public UUID create(UUID userId, ChallengeCreateRequestDto request) {
        Users user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        MiniChallenges challenge = MiniChallenges.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .challengeSubType(request.getChallengeSubType())
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

    // GET /challenges/active — 진행 중(IN_PROGRESS) 챌린지 조회 (없으면 null)
    @Transactional(readOnly = true)
    public ChallengeActiveResponseDto getActiveChallenge(UUID userId) {
        return miniChallengesRepository.findFirstByUserIdAndStatus(userId, MiniChallenges.ChallengeStatus.IN_PROGRESS)
                .map(c -> {
                    long target  = c.getTarget() != null ? c.getTarget() : 0L;
                    long current = c.getCurrentValue() != null ? c.getCurrentValue() : 0L;
                    int progress = target > 0 ? (int) Math.min(100, current * 100 / target) : 0;
                    return ChallengeActiveResponseDto.builder()
                            .challengeId(c.getId())
                            .title(c.getTitle())
                            .description(c.getDescription())
                            .category(c.getCategory())
                            .challengeSubType(c.getChallengeSubType() != null ? c.getChallengeSubType().name() : null)
                            .challengeType(c.getChallengeType() != null ? c.getChallengeType().name() : null)
                            .target(c.getTarget())
                            .currentValue(current)
                            .progressPercent(progress)
                            .estimatedSaving(c.getEstimatedSaving())
                            .ticker(c.getRewardStockTicker())
                            .build();
                })
                .orElse(null);
    }

    // 거래 발생 시 진행 업데이트 (TransactionConsumer에서 호출)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID userId, String category, String senderName,
                               LocalDateTime transactionAt, long amount) {
        if (!challengeRedisService.exists(userId)) return;

        Map<Object, Object> cache = challengeRedisService.get(userId);
        MiniChallenges.ChallengeSubType subType =
                MiniChallenges.ChallengeSubType.valueOf((String) cache.get("challengeSubType"));

        if (!matchesChallenge(subType, category, senderName, transactionAt)) return;

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
            miniChallengesRepository.findById(challengeId).ifPresent(challenge -> {
                challenge.fail();
                notificationService.saveAndSend(
                        userId,
                        Notifications.NotificationType.CHALLENGE_FAILED,
                        "챌린지 종료",
                        "\"" + challenge.getTitle() + "\" 챌린지가 종료되었어요. 다음엔 꼭 성공해봐요!"
                );
            });
            challengeRedisService.delete(userId);
            log.info("[Challenge] 한도 초과 즉시 실패 — userId: {}, challengeId: {}", userId, challengeId);
        }
    }

    // 알림 발송 + DB 동기화
    private void sendThresholdNotification(UUID userId, String challengeId, int threshold) {
        syncToDb(userId, UUID.fromString(challengeId));
        miniChallengesRepository.findById(UUID.fromString(challengeId)).ifPresent(challenge -> {
            try {
                String nagMessage = challengeAgentService.nag(userId, challenge, threshold).getNagMessage();
                Notifications.NotificationType nagType = switch (threshold) {
                    case 50 -> Notifications.NotificationType.NAG_50;
                    case 80 -> Notifications.NotificationType.NAG_80;
                    default -> Notifications.NotificationType.NAG_90;
                };
                notificationService.saveAndSend(userId, nagType, "챌린지 " + threshold + "% 소비!", nagMessage);
            } catch (Exception e) {
                log.warn("[Challenge] nag 알림 실패 — userId: {}, threshold: {}%, 사유: {}", userId, threshold, e.getMessage());
            }
        });
    }

    private boolean matchesChallenge(MiniChallenges.ChallengeSubType subType,
                                     String category, String senderName,
                                     LocalDateTime transactionAt) {
        String sender = senderName != null ? senderName : "";
        int hour = transactionAt != null ? transactionAt.getHour() : -1;

        return switch (subType) {
            case COFFEE     -> "카페".equals(category);
            case DELIVERY   -> "식비".equals(category) && DELIVERY_SENDERS.contains(sender);
            case ALCOHOL    -> "식비".equals(category) && ALCOHOL_KEYWORDS.stream().anyMatch(sender::contains);
            case LATE_NIGHT -> "식비".equals(category) && (hour >= 23 || hour <= 4);
            case LUNCH      -> "식비".equals(category) && (hour >= 11 && hour <= 14);
            case SHOPPING   -> "쇼핑".equals(category) && SHOPPING_SENDERS.contains(sender);
            case TAXI       -> "교통".equals(category) && sender.contains("택시");
        };
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
