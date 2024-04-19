package com.clova.anifriends.domain.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.clova.anifriends.base.BaseIntegrationTest;
import com.clova.anifriends.domain.applicant.Applicant;
import com.clova.anifriends.domain.common.event.ImageDeletionEvent;
import com.clova.anifriends.domain.recruitment.Recruitment;
import com.clova.anifriends.domain.recruitment.RecruitmentImage;
import com.clova.anifriends.domain.recruitment.dto.request.RecruitmentStatusFilter;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsResponse;
import com.clova.anifriends.domain.recruitment.repository.RecruitmentCacheRepository;
import com.clova.anifriends.domain.recruitment.repository.RecruitmentRepository;
import com.clova.anifriends.domain.recruitment.support.fixture.RecruitmentFixture;
import com.clova.anifriends.domain.recruitment.vo.RecruitmentInfo;
import com.clova.anifriends.domain.shelter.Shelter;
import com.clova.anifriends.domain.shelter.repository.ShelterRepository;
import com.clova.anifriends.domain.shelter.support.ShelterFixture;
import com.clova.anifriends.domain.volunteer.Volunteer;
import com.clova.anifriends.domain.volunteer.support.VolunteerFixture;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

public class RecruitmentIntegrationTest extends BaseIntegrationTest {

    private static final String RECRUITMENT_CACHE_KEY = "recruitment:count";

    @Autowired
    RecruitmentService recruitmentService;

    @Autowired
    ShelterRepository shelterRepository;

    @Autowired
    RecruitmentRepository recruitmentRepository;

    @Autowired
    RecruitmentCacheRepository recruitmentCacheRepository;

    @Autowired
    RedisTemplate<String, Long> redisTemplate;


    @Nested
    @DisplayName("registerRecruitment 메서드 호출 시")
    class RegisterRecruitmentTest {

        @Test
        @DisplayName("성공: 캐시 카운트를 증가시킨다.")
        void plusOneToRecruitmentCount() {
            // given
            Shelter shelter = ShelterFixture.shelter();
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            shelterRepository.save(shelter);
            recruitmentRepository.save(recruitment);
            recruitmentCacheRepository.saveRecruitment(recruitment);
            long count = recruitmentCacheRepository.getTotalNumberOfRecruitments();

            // when
            recruitmentService.registerRecruitment(
                shelter.getShelterId(),
                recruitment.getTitle(),
                recruitment.getStartTime(),
                recruitment.getEndTime(),
                recruitment.getDeadline(),
                recruitment.getCapacity(),
                recruitment.getContent(),
                recruitment.getImages()
            );

            // then
            assertThat(recruitmentCacheRepository.getTotalNumberOfRecruitments())
                .isEqualTo(count + 1);
        }
    }

    @Nested
    @DisplayName("updateRecruitment 메서드 호출 시")
    class UpdateRecruitmentTest {

        Shelter shelter;
        Recruitment recruitment;

        @BeforeEach
        void setUp() {
            shelter = ShelterFixture.shelter();
            recruitment = RecruitmentFixture.recruitment(shelter);
            shelterRepository.save(shelter);
            recruitmentRepository.save(recruitment);
        }

        @Test
        @DisplayName("성공: 이미지 url 입력값이 현재 이미지와 같을 때, 새로운 엔티티는 생성되지 않는다.")
        void updateRecruitmentWhenNoMoreEntities() {
            //given
            List<String> givenImageUrls = recruitment.getImages();

            //when
            recruitmentService.updateRecruitment(shelter.getShelterId(),
                recruitment.getRecruitmentId(), null, null, null,
                null, null, null, givenImageUrls);

            //then
            List<RecruitmentImage> recruitmentImages = entityManager.createQuery(
                    "select ri from RecruitmentImage ri", RecruitmentImage.class)
                .getResultList();
            assertThat(recruitmentImages).hasSize(givenImageUrls.size());
        }
    }

    @Nested
    @DisplayName("deleteRecruitment 메서드 호출 시")
    class DeleteRecruitmentTest {

        Shelter shelter;
        Recruitment recruitment;

        @BeforeEach
        void setUp() {
            shelter = ShelterFixture.shelter();
            recruitment = RecruitmentFixture.recruitment(shelter);
            shelterRepository.save(shelter);
        }

        @Test
        @DisplayName("성공")
        void deleteRecruitment() {
            //given
            List<String> imageUrls = List.of("image1", "image2");
            recruitment.updateRecruitment(null, null, null, null, null, null, imageUrls);
            recruitmentRepository.save(recruitment);

            //when
            recruitmentService.deleteRecruitment(shelter.getShelterId(),
                recruitment.getRecruitmentId());

            //then
            assertThat(events.stream(ImageDeletionEvent.class).count()).isEqualTo(1);
            Recruitment findRecruitment = entityManager.find(Recruitment.class,
                recruitment.getRecruitmentId());
            assertThat(findRecruitment).isNull();
            List<RecruitmentImage> recruitmentImages = entityManager.createQuery(
                    "select ri from RecruitmentImage ri", RecruitmentImage.class)
                .getResultList();
            assertThat(recruitmentImages).isEmpty();
        }

        @Test
        @DisplayName("성공: 신청자가 있는 경우")
        void deleteRecruitmentWhenExistsApplicants() {
            //given
            Volunteer volunteer = VolunteerFixture.volunteer();
            volunteerRepository.save(volunteer);
            recruitmentRepository.save(recruitment);
            Applicant applicant = new Applicant(recruitment, volunteer);
            applicantRepository.save(applicant);

            //when
            recruitmentService.deleteRecruitment(shelter.getShelterId(),
                recruitment.getRecruitmentId());

            //then
            Recruitment findRecruitment = entityManager.find(Recruitment.class,
                recruitment.getRecruitmentId());
            assertThat(findRecruitment).isNull();
        }

        @Test
        @DisplayName("성공: 캐시 카운트를 감소시킨다.")
        void MinusOneToRecruitmentCount() {
            // given
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            Recruitment savedRecruitment = recruitmentRepository.save(recruitment);
            long count = recruitmentCacheRepository.getTotalNumberOfRecruitments();

            // when
            recruitmentService.deleteRecruitment(
                shelter.getShelterId(),
                savedRecruitment.getRecruitmentId()
            );

            // then
            assertThat(recruitmentRepository.count()).isEqualTo(
                recruitmentCacheRepository.getTotalNumberOfRecruitments());
        }
    }

    @Nested
    @DisplayName("autoCloseRecruitment 메서드 호출 시")
    class AutoCloseRecruitmentTest {

        Shelter shelter;

        @BeforeEach
        void setUp() {
            shelter = ShelterFixture.shelter();
            shelterRepository.save(shelter);
        }

        @Test
        @DisplayName("성공: 저장소 업데이트 됨")
        void autoCloseRecruitment() {
            //given
            Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
            RecruitmentInfo recruitmentInfo = new RecruitmentInfo(recruitment.getStartTime(),
                recruitment.getEndTime(), recruitment.getDeadline(), recruitment.isClosed(),
                recruitment.getCapacity());
            LocalDateTime deadlineBeforeNow = LocalDateTime.now().minusDays(1);
            ReflectionTestUtils.setField(recruitmentInfo, "deadline", deadlineBeforeNow);
            ReflectionTestUtils.setField(recruitment, "info", recruitmentInfo);
            recruitmentRepository.save(recruitment);

            //when
            recruitmentService.autoCloseRecruitment();

            //then
            Recruitment findRecruitment = entityManager.find(Recruitment.class,
                recruitment.getRecruitmentId());
            assertThat(findRecruitment.isClosed()).isTrue();
        }
    }

    @Nested
    @DisplayName("findRecruitmentsV2 실행 시")
    class FindRecruitmentsV2Test {

        @AfterEach
        void tearDown() {
            redisTemplate.delete(RECRUITMENT_CACHE_KEY);
        }

        @Nested
        @DisplayName("캐시한 봉사 모집글 목록 카운트 조회하면")
        class GetCachedRecruitmentsTest {

            @Test
            @DisplayName("성공: 캐시 카운트를 가져온다.")
            void getCachedRecruitmentCount() {
                // given
                Shelter shelter = ShelterFixture.shelter();
                Recruitment recruitment = RecruitmentFixture.recruitment(shelter);
                shelterRepository.save(shelter);
                Recruitment savedRecruitment = recruitmentRepository.save(recruitment);

                String keyword = null;
                LocalDate startDate = null;
                LocalDate endDate = null;
                String isClosed = RecruitmentStatusFilter.ALL.getName();
                KeywordCondition allContains = new KeywordCondition(true, true, true);
                PageRequest pageRequest = PageRequest.of(0, 10);

                // when
                FindRecruitmentsResponse dbRecruitmentCountResponse = recruitmentService.findRecruitmentsV2(
                    keyword, startDate, endDate,
                    RecruitmentStatusFilter.valueOf(isClosed).getIsClosed(), allContains,
                    savedRecruitment.getCreatedAt(),
                    savedRecruitment.getRecruitmentId(), pageRequest
                );

                FindRecruitmentsResponse cachedRecruitmentCountResponse = recruitmentService.findRecruitmentsV2(
                    keyword, startDate, endDate,
                    RecruitmentStatusFilter.valueOf(isClosed).getIsClosed(), allContains,
                    savedRecruitment.getCreatedAt(), savedRecruitment.getRecruitmentId(),
                    pageRequest
                );

                // then
                assertThat(dbRecruitmentCountResponse.pageInfo().totalElements()).isEqualTo(
                    cachedRecruitmentCountResponse.pageInfo().totalElements());
            }
        }
    }
}
