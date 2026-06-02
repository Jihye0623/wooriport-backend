package com.wooriport.core_api.service;

import com.wooriport.core_api.repository.MiniChallengesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeRedisRecovery implements ApplicationRunner {

    private final MiniChallengesRepository miniChallengesRepository;
    private final ChallengeRedisService challengeRedisService;

    @Override
    public void run(ApplicationArguments args) {
        var challenges = miniChallengesRepository.findAllInProgress();
        challenges.forEach(c -> challengeRedisService.save(c.getUser().getId(), c));
        log.info("[Challenge] Redis 복구 완료 — {}건", challenges.size());
    }
}
