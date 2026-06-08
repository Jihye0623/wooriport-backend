package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.MiniChallenges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MiniChallengesRepository extends JpaRepository<MiniChallenges, UUID> {

    // 대시보드: 사용자의 진행 중(IN_PROGRESS) 챌린지 1건 (없으면 empty)
    @Query("SELECT c FROM MiniChallenges c WHERE c.user.id = :userId AND c.status = com.wooriport.core_api.domain.MiniChallenges.ChallengeStatus.IN_PROGRESS")
    Optional<MiniChallenges> findInProgressByUserId(@Param("userId") UUID userId);

    // 만료된 IN_PROGRESS 챌린지 (startedAt + 7일 <= 지금)
    @Query("SELECT c FROM MiniChallenges c WHERE c.status = 'IN_PROGRESS' AND c.startedAt <= :expiredBefore")
    List<MiniChallenges> findExpired(@Param("expiredBefore") LocalDateTime expiredBefore);

    // 서버 재시작 시 Redis 복구용 — user JOIN FETCH로 LazyInitializationException 방지
    @Query("SELECT c FROM MiniChallenges c JOIN FETCH c.user WHERE c.status = com.wooriport.core_api.domain.MiniChallenges.ChallengeStatus.IN_PROGRESS")
    List<MiniChallenges> findAllInProgress();

    // 월간 리포트 생성용 — 해당 월에 시작된 챌린지
    @Query("SELECT c FROM MiniChallenges c WHERE c.user.id = :userId AND c.startedAt >= :from AND c.startedAt < :to")
    List<MiniChallenges> findByUserIdAndMonth(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 월간 리포트 조회용 — 해당 월에 완료된 챌린지
    @Query("SELECT c FROM MiniChallenges c WHERE c.user.id = :userId AND c.completedAt >= :from AND c.completedAt < :to")
    List<MiniChallenges> findByUserIdAndCompletedAtMonth(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
