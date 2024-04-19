package com.clova.anifriends.domain.recruitment.repository;

import com.clova.anifriends.domain.common.PageInfo;
import com.clova.anifriends.domain.recruitment.Recruitment;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsResponse;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsResponse.FindRecruitmentResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

@Repository
public class RecruitmentRedisRepository implements RecruitmentCacheRepository {

    private static final String RECRUITMENT_KEY = "recruitment";
    private static final String RECRUITMENT_COUNT_KEY = "recruitment:count";
    private static final int UNTIL_LAST_ELEMENT = -1;
    private static final int MAX_CACHED_SIZE = 30;
    private static final int ZERO = 0;
    public static final ZoneOffset CREATED_AT_SCORE_TIME_ZONE = ZoneOffset.UTC;

    private final ZSetOperations<String, FindRecruitmentResponse> cachedRecruitments;
    private final ValueOperations<String, Long> cachedRecruitmentsCount;
    private final RecruitmentRepository recruitmentRepository;

    public RecruitmentRedisRepository(
        RedisTemplate<String, FindRecruitmentResponse> findRecruitmentTemplate,
        RedisTemplate<String, Long> countTemplate, RecruitmentRepository recruitmentRepository) {
        this.cachedRecruitments = findRecruitmentTemplate.opsForZSet();
        this.cachedRecruitmentsCount = countTemplate.opsForValue();
        this.recruitmentRepository = recruitmentRepository;
    }

    /**
     * 봉사 모집글을 캐시에 저장하고 캐싱된 카운트를 증가시킵니다.
     *
     * @param recruitment
     */
    @Override
    public void saveRecruitment(final Recruitment recruitment) {
        FindRecruitmentResponse recruitmentResponse = FindRecruitmentResponse.from(recruitment);
        long createdAtScore = getCreatedAtScore(recruitment);
        cachedRecruitments.add(RECRUITMENT_KEY, recruitmentResponse, createdAtScore);
        cachedRecruitmentsCount.increment(RECRUITMENT_COUNT_KEY);
        trimCache();
    }

    private long getCreatedAtScore(Recruitment recruitment) {
        return recruitment.getCreatedAt().toEpochSecond(CREATED_AT_SCORE_TIME_ZONE);
    }

    private void trimCache() {
        cachedRecruitments.removeRange(RECRUITMENT_KEY, ZERO, -MAX_CACHED_SIZE - 1L);
    }

    /**
     * 캐시된 Recruitment dto 리스트를 첫번째 요소부터 size만큼 조회합니다. size가 최대 캐싱 사이즈를 초과하는 경우 db에서 조회합니다.
     *
     * @param size 조회할 사이즈
     * @return FindRecruitmentsResponse 캐시 혹은 db에서 조회한 결과
     */
    @Override
    public FindRecruitmentsResponse findRecruitments(int size) {
        Set<FindRecruitmentResponse> recruitments = Objects.requireNonNull(
            cachedRecruitments.reverseRange(RECRUITMENT_KEY, ZERO, size - 1L));
        long count = getTotalNumberOfRecruitments();
        PageInfo pageInfo = PageInfo.of(count, count > size);
        if (recruitments.size() >= size) {
            List<FindRecruitmentResponse> content = recruitments.stream()
                .limit(size)
                .toList();
            return new FindRecruitmentsResponse(content, pageInfo);
        }

        PageRequest pageRequest = PageRequest.of(ZERO, size);
        List<FindRecruitmentResponse> content = getRecruitmentsV2(pageRequest)
            .map(FindRecruitmentResponse::from)
            .toList();
        return new FindRecruitmentsResponse(content, pageInfo);
    }

    private Slice<Recruitment> getRecruitmentsV2(PageRequest pageRequest) {
        return recruitmentRepository.findRecruitmentsV2(null, null,
            null, null, null, null, null, pageRequest);
    }

    /**
     * 캐시된 봉사 모집글을 제거하고 카운트를 감소시킵니다.
     *
     * @param recruitment
     * @return
     */
    @Override
    public long deleteRecruitment(final Recruitment recruitment) {
        FindRecruitmentResponse recruitmentResponse = FindRecruitmentResponse.from(recruitment);
        Long number = cachedRecruitments.remove(RECRUITMENT_KEY, recruitmentResponse);
        cachedRecruitmentsCount.decrement(RECRUITMENT_COUNT_KEY);
        return Objects.isNull(number) ? 0 : number;
    }

    /**
     * 캐시된 봉사 모집글 중 모집 기간이 종료된 요소를 업데이트합니다.
     */
    @Override
    public void closeRecruitmentsIfNeedToBe() {
        LocalDateTime now = LocalDateTime.now();
        Set<FindRecruitmentResponse> findRecruitments = cachedRecruitments.range(RECRUITMENT_KEY,
            ZERO, UNTIL_LAST_ELEMENT);
        if (Objects.nonNull(findRecruitments)) {
            Map<FindRecruitmentResponse, FindRecruitmentResponse> cachedKeyAndUpdatedValue
                = new HashMap<>();
            findRecruitments.stream()
                .filter(recruitment -> needToClose(recruitment, now))
                .forEach(recruitment -> {
                    FindRecruitmentResponse closedRecruitment = closeCachedRecruitment(recruitment);
                    cachedKeyAndUpdatedValue.put(recruitment, closedRecruitment);
                });

            cachedKeyAndUpdatedValue.forEach((key, value) -> {
                cachedRecruitments.remove(RECRUITMENT_KEY, key);
                long createdAtScore = value.recruitmentCreatedAt()
                    .toEpochSecond(CREATED_AT_SCORE_TIME_ZONE);
                cachedRecruitments.add(RECRUITMENT_KEY, value, createdAtScore);
            });
        }
    }

    private boolean needToClose(FindRecruitmentResponse recruitment, LocalDateTime now) {
        boolean isClosed = recruitment.recruitmentIsClosed();
        LocalDateTime deadline = recruitment.recruitmentDeadline();
        boolean notYetClosed = !isClosed;
        boolean passedDeadline = deadline.isBefore(now) || deadline.isEqual(now);
        return notYetClosed && passedDeadline;
    }

    private FindRecruitmentResponse closeCachedRecruitment(FindRecruitmentResponse recruitment) {
        return new FindRecruitmentResponse(
            recruitment.recruitmentId(),
            recruitment.recruitmentTitle(),
            recruitment.recruitmentStartTime(),
            recruitment.recruitmentEndTime(),
            recruitment.recruitmentDeadline(),
            true,
            recruitment.recruitmentApplicantCount(),
            recruitment.recruitmentCapacity(),
            recruitment.shelterName(),
            recruitment.shelterImageUrl(),
            recruitment.recruitmentCreatedAt()
        );
    }

    @Override
    public long getTotalNumberOfRecruitments() {
        Object cachedCount = cachedRecruitmentsCount.get(RECRUITMENT_COUNT_KEY);
        if (Objects.nonNull(cachedCount)) {
            return ((Integer) cachedCount).longValue();
        }
        long dbCount = recruitmentRepository.count();
        cachedRecruitmentsCount.set(RECRUITMENT_COUNT_KEY, dbCount);
        return dbCount;
    }
}
