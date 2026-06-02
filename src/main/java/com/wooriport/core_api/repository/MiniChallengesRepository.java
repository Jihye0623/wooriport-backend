package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.MiniChallenges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MiniChallengesRepository extends JpaRepository<MiniChallenges, UUID> {

    // 만료된 IN_PROGRESS 챌린지 (startedAt + 7일 <= 지금)
    @Query("SELECT c FROM MiniChallenges c WHERE c.status = 'IN_PROGRESS' AND c.startedAt <= :expiredBefore")
    List<MiniChallenges> findExpired(@Param("expiredBefore") LocalDateTime expiredBefore);

    // 서버 재시작 시 Redis 복구용 — user JOIN FETCH로 LazyInitializationException 방지
    @Query("SELECT c FROM MiniChallenges c JOIN FETCH c.user WHERE c.status = com.wooriport.core_api.domain.MiniChallenges.ChallengeStatus.IN_PROGRESS")
    List<MiniChallenges> findAllInProgress();
}
