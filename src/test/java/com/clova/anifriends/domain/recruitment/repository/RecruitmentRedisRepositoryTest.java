package com.clova.anifriends.domain.recruitment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.clova.anifriends.base.BaseIntegrationTest;
import com.clova.anifriends.domain.recruitment.Recruitment;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsResponse;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsResponse.FindRecruitmentResponse;
import com.clova.anifriends.domain.recruitment.support.fixture.RecruitmentFixture;
import com.clova.anifriends.domain.recruitment.vo.RecruitmentInfo;
import com.clova.anifriends.domain.shelter.Shelter;
import com.clova.anifriends.domain.shelter.support.ShelterFixture;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RecruitmentRedisRepositoryTest extends BaseIntegrationTest {

    private static final String RECRUITMENT_KEY = "recruitment";
    private static final int ZERO = 0;
    private static final int ALL_ELEMENT = -1;

    @Autowired
    RecruitmentRedisRepository recruitmentRedisRepository;

    @Autowired
    RedisTemplate<String, FindRecruitmentResponse> redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.opsForZSet()
            .removeRange(RECRUITMENT_KEY, ZERO, ALL_ELEMENT);
    }

    @Nested
    @DisplayName("saveRecruitment 메서드 호출 시")
    class SaveRecruitmentTest {

        Shelter shelter;

        @BeforeEach
        void setUp() {
            shelter = ShelterFixture.shelter();
            shelterRepository.save(shelter);
        }

        @Test
        @DisplayName("성공: 새로운 Recruitment 캐싱")
        void registerRecruitment() {
            //given
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            recruitmentRepository.save(recruitment);

            //when
            recruitmentRedisRepository.saveRecruitment(recruitment);

            //then
            PageRequest pageRequest = PageRequest.of(0, 20);
            FindRecruitmentsResponse response = recruitmentRedisRepository.findRecruitments(
                pageRequest.getPageSize()
            );
            List<FindRecruitmentResponse> content = response.recruitments();
            assertThat(content).hasSize(1);
            FindRecruitmentResponse recruitmentResponse = content.get(0);
            assertThat(recruitmentResponse.recruitmentId())
                .isEqualTo(recruitment.getRecruitmentId());
        }

        @Test
        @DisplayName("성공: 카운트 값이 1 증가한다.")
        void increaseCachedCount() {
            //given
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            recruitmentRepository.save(recruitment);
            long cachedCount = recruitmentRedisRepository.getTotalNumberOfRecruitments();

            //when
            recruitmentRedisRepository.saveRecruitment(recruitment);

            //then
            assertThat(recruitmentRedisRepository.getTotalNumberOfRecruitments())
                .isEqualTo(cachedCount + 1);
        }

        @Test
        @DisplayName("성공: 새로운 Recruitment 100개 생성, 30개 캐싱")
        void overflowTest() {
            //given
            List<Recruitment> recruitments = IntStream.range(0, 100)
                .mapToObj(i -> RecruitmentFixture.recruitment(shelter))
                .toList();
            recruitmentRepository.saveAll(recruitments);
            LocalDateTime now = LocalDateTime.now();
            int hour = 0;
            for (Recruitment recruitment : recruitments) {
                ReflectionTestUtils.setField(recruitment, "createdAt", now.plusHours(hour++));
            }

            //when
            for (Recruitment recruitment : recruitments) {
                recruitmentRedisRepository.saveRecruitment(recruitment);
            }

            //then
            List<Long> recruitmentIdsDesc = recruitments.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(30)
                .map(Recruitment::getRecruitmentId)
                .toList();

            Set<FindRecruitmentResponse> findRecruitments = redisTemplate.opsForZSet()
                .reverseRange(RECRUITMENT_KEY, 0, -1);
            assertThat(findRecruitments).hasSize(30);
            assertThat(findRecruitments).map(FindRecruitmentResponse::recruitmentId)
                .containsExactlyElementsOf(recruitmentIdsDesc);
        }

        @Test
        @DisplayName("성공: 동시에 새로운 봉사 모집글이 추가되는 경우 손실되지 않는다.")
        void doseNotLost() throws InterruptedException {
            //given
            int count = 100;
            int cachedSize = 30;
            List<Recruitment> recruitments = IntStream.range(0, count)
                .mapToObj(i -> RecruitmentFixture.recruitment(shelter))
                .toList();
            recruitmentRepository.saveAll(recruitments);
            LocalDateTime now = LocalDateTime.now();
            int hour = 0;
            for (Recruitment recruitment : recruitments) {
                ReflectionTestUtils.setField(recruitment, "createdAt", now.plusHours(hour++));
            }

            ExecutorService executorService = Executors.newFixedThreadPool(count);
            CountDownLatch latch = new CountDownLatch(count);

            //when
            for(int i=0; i<count; i++) {
                int finalI = i;
                executorService.submit(() -> {
                    try {
                        recruitmentRedisRepository.saveRecruitment(recruitments.get(finalI));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();

            //then
            Set<FindRecruitmentResponse> result = redisTemplate.opsForZSet()
                .range(RECRUITMENT_KEY, ZERO, ALL_ELEMENT);
            assertThat(result).hasSize(cachedSize);
        }
    }

    @Nested
    @DisplayName("findRecruitments 메서드 호출 시")
    class FindAllTest {

        Shelter shelter;

        @BeforeEach
        void setUp() {
            shelter = ShelterFixture.shelter();
            shelterRepository.save(shelter);
        }

        @Test
        @DisplayName("성공: 봉사 모집글이 없으면 빈 리스트를 반환한다.")
        void findAllWhenEmpty() {
            //given
            PageRequest pageRequest = PageRequest.of(0, 20);

            //when
            FindRecruitmentsResponse response = recruitmentRedisRepository.findRecruitments(
                pageRequest.getPageSize()
            );

            //then
            assertThat(response.recruitments()).isEmpty();
        }

        @Test
        @DisplayName("성공: 봉사 모집글이 있으면 사이즈 조회하여 반환한다.")
        void findAllWhenCached() {
            //given
            List<Recruitment> recruitments = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            IntStream.range(0, 100)
                .forEach(i -> {
                    Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
                    recruitments.add(recruitment);
                });
            recruitmentRepository.saveAll(recruitments);
            int hour = 0;
            for (Recruitment recruitment : recruitments) {
                ReflectionTestUtils.setField(recruitment, "createdAt", now.plusHours(hour++));
                recruitmentRedisRepository.saveRecruitment(recruitment);
            }
            PageRequest pageRequest = PageRequest.of(0, 20);

            //when
            FindRecruitmentsResponse response = recruitmentRedisRepository.findRecruitments(
                pageRequest.getPageSize()
            );

            //then
            List<Long> recruitmentIdsDesc = recruitments.stream()
                .map(Recruitment::getRecruitmentId)
                .filter(i -> i > 80)
                .sorted(Comparator.reverseOrder())
                .toList();

            assertThat(response.recruitments())
                .hasSize(20)
                .map(FindRecruitmentResponse::recruitmentId)
                .containsExactlyElementsOf(recruitmentIdsDesc);
        }

        @Test
        @DisplayName("성공: 캐싱할 최대 크기보다 크면 DB에서 조회한다.")
        void findAllOverMaxCacheSize_ThenFindDB() {
            //given
            int pageSize = 50;
            PageRequest pageRequest = PageRequest.of(0, pageSize);
            List<Recruitment> recruitments = RecruitmentFixture.recruitments(shelter, 100);
            recruitmentRepository.saveAll(recruitments);
            int hour = 0;
            LocalDateTime now = LocalDateTime.now();
            for (Recruitment recruitment : recruitments) {
                ReflectionTestUtils.setField(recruitment, "createdAt", now.plusHours(hour++));
                recruitmentRedisRepository.saveRecruitment(recruitment);
            }

            //when
            FindRecruitmentsResponse response = recruitmentRedisRepository.findRecruitments(
                pageRequest.getPageSize()
            );

            //then
            assertThat(response.recruitments()).hasSize(50);
            assertThat(response.pageInfo().hasNext()).isTrue();
        }
    }

    @Nested
    @DisplayName("deleteCachedRecruitment 메서드 실행 시")
    class DeleteCachedRecruitmentTest {

        Shelter shelter;

        @BeforeEach
        void setUp() {
            shelter = ShelterFixture.shelter();
            shelterRepository.save(shelter);
        }

        @Test
        @DisplayName("성공: 캐시된 Recruitment 삭제 됨")
        void deleteCachedRecruitment() {
            //given
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            recruitmentRepository.save(recruitment);
            recruitmentRedisRepository.saveRecruitment(recruitment);

            //when
            recruitmentRedisRepository.deleteRecruitment(recruitment);

            //then
            ZSetOperations<String, FindRecruitmentResponse> cachedRecruitments
                = redisTemplate.opsForZSet();
            long createdAtScore = recruitment.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
            Set<FindRecruitmentResponse> recruitments = cachedRecruitments.rangeByScore(
                RECRUITMENT_KEY, createdAtScore, createdAtScore);
            assertThat(recruitments).isEmpty();
        }

        @Test
        @DisplayName("성공: 카운트 값이 1 감소한다.")
        void decreaseCachedCount() {
            //given
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            recruitmentRepository.save(recruitment);
            recruitmentRedisRepository.saveRecruitment(recruitment);
            long cachedCount = recruitmentRedisRepository.getTotalNumberOfRecruitments();

            //when
            recruitmentRedisRepository.deleteRecruitment(recruitment);

            //then
            assertThat(recruitmentRedisRepository.getTotalNumberOfRecruitments())
                .isEqualTo(cachedCount - 1);
        }

        @Test
        @DisplayName("성공: 캐시된 Recruitment 없는 경우 무시 됨")
        void ignoreWhenCachedRecruitmentDoesNotExists() {
            //given
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            recruitmentRepository.save(recruitment);

            //when
            recruitmentRedisRepository.deleteRecruitment(recruitment);

            //then
            ZSetOperations<String, FindRecruitmentResponse> cachedRecruitments
                = redisTemplate.opsForZSet();
            long createdAtScore = recruitment.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
            Set<FindRecruitmentResponse> recruitments = cachedRecruitments.rangeByScore(
                RECRUITMENT_KEY, createdAtScore, createdAtScore);
            assertThat(recruitments).isEmpty();
        }
    }

    @Nested
    @DisplayName("closeRecruitmentsIfNeedToBe 메서드 호출 시")
    class CloseRecruitmentsIfNeedToBeTest {

        Shelter shelter;

        @BeforeEach
        void setUp() {
            shelter = ShelterFixture.shelter();
            shelterRepository.save(shelter);
        }

        @Test
        @DisplayName("성공: A(마감 대상), B(마감 시간 전)")
        void closeRecruitmentsIfNeedToBe() {
            //given
            Recruitment recruitmentB = RecruitmentFixture.recruitment(shelter);
            Recruitment recruitmentA = RecruitmentFixture.recruitment(shelter);
            RecruitmentInfo recruitmentInfo = new RecruitmentInfo(recruitmentA.getStartTime(),
                recruitmentA.getEndTime(), recruitmentA.getDeadline(), recruitmentA.isClosed(),
                recruitmentA.getCapacity());
            LocalDateTime deadlineBeforeNow = LocalDateTime.now().minusDays(1);
            ReflectionTestUtils.setField(recruitmentInfo, "deadline", deadlineBeforeNow);
            ReflectionTestUtils.setField(recruitmentA, "info", recruitmentInfo);
            recruitmentRepository.save(recruitmentA);
            recruitmentRepository.save(recruitmentB);
            recruitmentRedisRepository.saveRecruitment(recruitmentA);
            recruitmentRedisRepository.saveRecruitment(recruitmentB);

            //when
            recruitmentRedisRepository.closeRecruitmentsIfNeedToBe();

            //then
            Set<FindRecruitmentResponse> cachedRecruitments = redisTemplate.opsForZSet()
                .range(RECRUITMENT_KEY, ZERO, ALL_ELEMENT);
            Optional<FindRecruitmentResponse> findRecruitmentA = cachedRecruitments.stream()
                .filter(FindRecruitmentResponse::recruitmentIsClosed)
                .findFirst();
            Optional<FindRecruitmentResponse> findRecruitmentB = cachedRecruitments.stream()
                .filter(recruitment -> !recruitment.recruitmentIsClosed())
                .findFirst();
            assertThat(findRecruitmentA).isNotEmpty();
            assertThat(findRecruitmentB).isNotEmpty();
            assertThat(findRecruitmentA.get().recruitmentIsClosed()).isTrue();
            assertThat(findRecruitmentB.get().recruitmentIsClosed()).isFalse();
        }

        @Test
        @DisplayName("성공: A(이미 마감 됨)")
        void closeRecruitmentsIfNeedToBeButAlreadyClosed() {
            //given
            Recruitment recruitmentA = RecruitmentFixture.recruitment(shelter);
            RecruitmentInfo recruitmentInfo = new RecruitmentInfo(recruitmentA.getStartTime(),
                recruitmentA.getEndTime(), recruitmentA.getDeadline(), recruitmentA.isClosed(),
                recruitmentA.getCapacity());
            ReflectionTestUtils.setField(recruitmentInfo, "isClosed", true);
            ReflectionTestUtils.setField(recruitmentA, "info", recruitmentInfo);
            recruitmentRepository.save(recruitmentA);
            recruitmentRedisRepository.saveRecruitment(recruitmentA);

            //when
            recruitmentRedisRepository.closeRecruitmentsIfNeedToBe();

            //then
            Set<FindRecruitmentResponse> cachedRecruitments = redisTemplate.opsForZSet()
                .range(RECRUITMENT_KEY, ZERO, ALL_ELEMENT);
            Optional<FindRecruitmentResponse> findRecruitmentA = cachedRecruitments.stream()
                .filter(FindRecruitmentResponse::recruitmentIsClosed)
                .findFirst();
            assertThat(findRecruitmentA).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("getTotalNumberOfRecruitments 메서드 호출 시")
    class GetRecruitmentsCountTest {

        @Test
        @DisplayName("성공: 요소의 총 개수를 반환한다.")
        void getCachedRecruitmentCountWhenNotExistInRedis() {
            // given
            Shelter shelter = ShelterFixture.shelter();
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            shelterRepository.save(shelter);
            recruitmentRepository.save(recruitment);

            // when
            Long recruitmentCount = recruitmentRedisRepository.getTotalNumberOfRecruitments();

            // then
            assertThat(recruitmentCount).isEqualTo(1);
        }
    }
}
