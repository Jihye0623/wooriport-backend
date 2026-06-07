package com.wooriport.core_api.service;

import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.repository.MiniChallengesRepository;
import com.wooriport.core_api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock MiniChallengesRepository miniChallengesRepository;
    @Mock UserRepository userRepository;
    @Mock ChallengeRedisService challengeRedisService;
    @Mock ChallengeAgentService challengeAgentService;
    @Mock NotificationService notificationService;
    @InjectMocks ChallengeService challengeService;

    private final UUID userId = UUID.randomUUID();
    private final UUID challengeId = UUID.randomUUID();
    // 점심(LUNCH) 챌린지 매칭 조건: category '식비' & 시간대 11~14시
    private final LocalDateTime lunchTime = LocalDateTime.of(2026, 5, 14, 12, 0);

    @Test
    @DisplayName("진행 중 챌린지(Redis)가 없으면 아무 것도 하지 않는다")
    void notInRedis_noOp() {
        given(challengeRedisService.exists(userId)).willReturn(false);

        challengeService.updateProgress(userId, "식비", "본죽", lunchTime, 1000L);

        verify(challengeRedisService, never()).increment(any(), anyLong());
    }

    @Test
    @DisplayName("거래가 챌린지 서브타입 조건과 안 맞으면 스킵한다")
    void subTypeMismatch_skips() {
        given(challengeRedisService.exists(userId)).willReturn(true);
        // COFFEE 챌린지(카페) 인데 식비 거래가 들어옴 → matchesChallenge false
        given(challengeRedisService.get(userId)).willReturn(amountCache("COFFEE", "10000", "4000", "0"));

        challengeService.updateProgress(userId, "식비", "본죽", lunchTime, 1000L);

        verify(challengeRedisService, never()).increment(any(), anyLong());
    }

    @Test
    @DisplayName("금액 누적이 50% 임계값에 도달하면 알림 임계값을 갱신한다")
    void amountReachesThreshold_notifies() {
        given(challengeRedisService.exists(userId)).willReturn(true);
        // LUNCH 챌린지(식비 11~14시). 목표 10000, 현재 4000 + 1000 = 5000 → 50%
        given(challengeRedisService.get(userId)).willReturn(amountCache("LUNCH", "10000", "4000", "0"));
        given(miniChallengesRepository.findById(any())).willReturn(Optional.empty());

        challengeService.updateProgress(userId, "식비", "본죽", lunchTime, 1000L);

        verify(challengeRedisService).increment(userId, 1000L);
        verify(challengeRedisService).updateNotifiedThreshold(userId, 50);
    }

    @Test
    @DisplayName("한도(100%)를 초과하면 챌린지를 실패 처리하고 Redis 키를 삭제한다")
    void amountExceedsLimit_failsAndDeletes() {
        MiniChallenges challenge = org.mockito.Mockito.mock(MiniChallenges.class);
        given(challengeRedisService.exists(userId)).willReturn(true);
        // LUNCH 챌린지. 목표 10000, 현재 9500 + 1000 = 10500 → 105%, 이미 90%까지 알림됨
        given(challengeRedisService.get(userId)).willReturn(amountCache("LUNCH", "10000", "9500", "90"));
        given(miniChallengesRepository.findById(challengeId)).willReturn(Optional.of(challenge));

        challengeService.updateProgress(userId, "식비", "본죽", lunchTime, 1000L);

        verify(challenge).fail();
        verify(challengeRedisService).delete(userId);
    }

    /** 금액(AMOUNT) 기반 챌린지의 Redis 캐시 스냅샷 */
    private Map<Object, Object> amountCache(String subType, String target,
                                            String currentValue, String notifiedThreshold) {
        Map<Object, Object> m = new HashMap<>();
        m.put("id", challengeId.toString());
        m.put("challengeSubType", subType);
        m.put("challengeType", MiniChallenges.ChallengeType.AMOUNT.name());
        m.put("target", target);
        m.put("currentValue", currentValue);
        m.put("notifiedThreshold", notifiedThreshold);
        return m;
    }
}
