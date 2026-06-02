package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.challenge.ChallengeCreateRequestDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.MiniChallengesRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final MiniChallengesRepository miniChallengesRepository;
    private final UserRepository userRepository;

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
        return challenge.getId();
    }
}
