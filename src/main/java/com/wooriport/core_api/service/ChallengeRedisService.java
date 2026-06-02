package com.wooriport.core_api.service;

import com.wooriport.core_api.domain.MiniChallenges;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChallengeRedisService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "challenge:";
    private static final Duration TTL = Duration.ofDays(8); // 1주 + 여유

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    public void save(UUID userId, MiniChallenges challenge) {
        String k = key(userId);
        redisTemplate.opsForHash().putAll(k, Map.of(
                "id",                 challenge.getId().toString(),
                "category",           challenge.getCategory(),
                "targetAmount",       challenge.getTargetAmount() != null ? challenge.getTargetAmount().toString() : "",
                "targetCount",        challenge.getTargetCount()  != null ? challenge.getTargetCount().toString()  : "",
                "currentAmount",      challenge.getCurrentAmount().toString(),
                "currentCount",       challenge.getCurrentCount().toString(),
                "notifiedThreshold",  challenge.getNotifiedThreshold().toString()
        ));
        redisTemplate.expire(k, TTL);
    }

    public Map<Object, Object> get(UUID userId) {
        return redisTemplate.opsForHash().entries(key(userId));
    }

    public boolean exists(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId)));
    }

    public void incrementAmount(UUID userId, long amount) {
        redisTemplate.opsForHash().increment(key(userId), "currentAmount", amount);
    }

    public void incrementCount(UUID userId, int count) {
        redisTemplate.opsForHash().increment(key(userId), "currentCount", count);
    }

    public void updateNotifiedThreshold(UUID userId, int threshold) {
        redisTemplate.opsForHash().put(key(userId), "notifiedThreshold", String.valueOf(threshold));
    }

    public void delete(UUID userId) {
        redisTemplate.delete(key(userId));
    }
}
