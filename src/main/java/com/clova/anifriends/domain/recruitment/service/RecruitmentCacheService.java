package com.clova.anifriends.domain.recruitment.service;

import com.clova.anifriends.domain.recruitment.repository.RecruitmentRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecruitmentCacheService {

    private final RedisTemplate<String, Long> redisTemplate;
    private final RecruitmentRepository recruitmentRepository;

    private static final Long RECRUITMENT_COUNT_NO_CACHE = -1L;

    public Long getRecruitmentCount(
        String cacheKey
    ) {
        Object cachedCount = redisTemplate.opsForValue().get(cacheKey);
        if (Objects.isNull(cachedCount)) {
            return RECRUITMENT_COUNT_NO_CACHE;
        }
        if (cachedCount instanceof Long) {
            return (Long) cachedCount;
        }
        return ((Integer) cachedCount).longValue();
    }

    public void registerRecruitmentCount(
        String cacheKey,
        Long count
    ) {
        redisTemplate.opsForValue().set(cacheKey, count);
    }

    public void plusOneToRecruitmentCount(
        String cacheKey
    ) {
        Long cachedCount = redisTemplate.opsForValue().get(cacheKey);

        if (Objects.nonNull(cachedCount)) {
            registerRecruitmentCount(cacheKey, cachedCount + 1L);
        }

        long dbRecruitmentCount = recruitmentRepository.count();

        registerRecruitmentCount(cacheKey, dbRecruitmentCount);
    }
}
