package com.wooriport.core_api.base.batch.scheduler;

import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.domain.Notifications;
import com.wooriport.core_api.repository.MiniChallengesRepository;
import com.wooriport.core_api.service.ChallengeAgentService;
import com.wooriport.core_api.service.ChallengeService;
import com.wooriport.core_api.service.ChallengeRedisService;
import com.wooriport.core_api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeScheduler {

    private final MiniChallengesRepository miniChallengesRepository;
    private final ChallengeService challengeService;
    private final ChallengeRedisService challengeRedisService;
    private final ChallengeAgentService challengeAgentService;
    private final NotificationService notificationService;

    /**
     * 매일 자정 실행 — 7일 경과 챌린지 판정
     * 테스트: "0 * * * * *" (매분)
     * 운영:   "0 0 0 * * *" (매일 자정)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkExpiredChallenges() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusDays(7);
        List<MiniChallenges> expired = miniChallengesRepository.findExpired(expiredBefore);

        if (expired.isEmpty()) return;

        log.info("[ChallengeScheduler] 만료 챌린지 {}건 처리 시작", expired.size());

        for (MiniChallenges challenge : expired) {
            UUID userId = challenge.getUser().getId();

            // Redis → DB 최종 동기화
            challengeService.syncToDb(userId, challenge.getId());

            // 동기화 후 최신 상태 반영
            miniChallengesRepository.flush();
            MiniChallenges updated = miniChallengesRepository.findById(challenge.getId()).orElse(challenge);

            boolean success = isSuccess(updated);

            if (success) {
                updated.complete();
                log.info("[ChallengeScheduler] 챌린지 성공 — id: {}", updated.getId());
                sendRewardNotification(userId, updated);
            } else {
                updated.fail();
                log.info("[ChallengeScheduler] 챌린지 실패 — id: {}", updated.getId());
                notificationService.saveAndSend(
                        userId,
                        Notifications.NotificationType.CHALLENGE_FAILED,
                        "챌린지 종료",
                        "\"" + updated.getTitle() + "\" 챌린지가 종료되었어요. 다음엔 꼭 성공해봐요!"
                );
            }

            challengeRedisService.delete(userId);
        }

        log.info("[ChallengeScheduler] 만료 챌린지 처리 완료");
    }

    private void sendRewardNotification(UUID userId, MiniChallenges challenge) {
        try {
            String rewardMessage = challengeAgentService.reward(userId, challenge).getMessage();
            notificationService.saveAndSend(
                    userId,
                    Notifications.NotificationType.CHALLENGE_COMPLETE,
                    "챌린지 성공!",
                    rewardMessage
            );
        } catch (Exception e) {
            log.warn("[ChallengeScheduler] reward 알림 실패 — id: {}, 사유: {}", challenge.getId(), e.getMessage());
            notificationService.saveAndSend(
                    userId,
                    Notifications.NotificationType.CHALLENGE_COMPLETE,
                    "챌린지 성공!",
                    "\"" + challenge.getTitle() + "\" 챌린지를 성공적으로 완료했어요!"
            );
        }
    }

    private boolean isSuccess(MiniChallenges c) {
        if (c.getTarget() == null || c.getTarget() <= 0) return false;
        return c.getCurrentValue() <= c.getTarget();
    }
}
