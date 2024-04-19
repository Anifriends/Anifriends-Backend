package com.clova.anifriends.domain.recruitment.service;

import static com.clova.anifriends.domain.recruitment.support.fixture.RecruitmentDtoFixture.FindShelterRecruitmentsResponse;
import static com.clova.anifriends.domain.recruitment.support.fixture.RecruitmentDtoFixture.findRecruitmentDetailResponse;
import static com.clova.anifriends.domain.recruitment.support.fixture.RecruitmentDtoFixture.findRecruitmentsByShelterResponse;
import static com.clova.anifriends.domain.recruitment.support.fixture.RecruitmentFixture.recruitment;
import static com.clova.anifriends.domain.shelter.support.ShelterFixture.shelter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import com.clova.anifriends.domain.common.PageInfo;
import com.clova.anifriends.domain.common.event.ImageDeletionEvent;
import com.clova.anifriends.domain.recruitment.Recruitment;
import com.clova.anifriends.domain.recruitment.dto.request.RecruitmentStatusFilter;
import com.clova.anifriends.domain.recruitment.dto.response.FindCompletedRecruitmentsResponse;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentDetailResponse;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsByShelterResponse;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsResponse;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsResponse.FindRecruitmentResponse;
import com.clova.anifriends.domain.recruitment.dto.response.FindShelterRecruitmentsResponse;
import com.clova.anifriends.domain.recruitment.exception.RecruitmentNotFoundException;
import com.clova.anifriends.domain.recruitment.repository.RecruitmentCacheRepository;
import com.clova.anifriends.domain.recruitment.repository.RecruitmentRepository;
import com.clova.anifriends.domain.recruitment.support.fixture.RecruitmentFixture;
import com.clova.anifriends.domain.shelter.Shelter;
import com.clova.anifriends.domain.shelter.exception.ShelterNotFoundException;
import com.clova.anifriends.domain.shelter.repository.ShelterRepository;
import com.clova.anifriends.domain.shelter.support.ShelterFixture;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceTest {

    @InjectMocks
    RecruitmentService recruitmentService;

    @Mock
    ShelterRepository shelterRepository;

    @Mock
    RecruitmentRepository recruitmentRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    RecruitmentCacheRepository recruitmentCacheRepository;

    @Mock
    RecruitmentCacheService recruitmentCacheService;

    @Nested
    @DisplayName("registerRecruitment 메서드 실행 시")
    class RegisterRecruitmentTest {

        Long shelterId = 1L;
        String title = "title";
        LocalDateTime startTime = LocalDateTime.now().plusHours(1);
        LocalDateTime endTime = startTime.plusMinutes(1);
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(1);
        Integer capacity = 10;
        String content = "content";
        List<String> imageUrls = List.of();

        @Test
        @DisplayName("성공")
        void registerRecruitment() {
            //given
            Shelter shelter = ShelterFixture.shelter();

            given(shelterRepository.findById(anyLong())).willReturn(Optional.of(shelter));

            //when
            recruitmentService.registerRecruitment(shelterId, title, startTime, endTime, deadline,
                capacity, content, imageUrls);

            //then
            then(recruitmentRepository).should().save(any());
        }

        @Test
        @DisplayName("예외(ShelterNotFoundException): 존재하지 않는 shelter")
        void exceptionWhenShelterNotFound() {
            //given
            given(shelterRepository.findById(anyLong())).willReturn(Optional.empty());

            //when
            //then
            assertThatThrownBy(() -> recruitmentService.registerRecruitment(
                shelterId, title, startTime, endTime, deadline, capacity, content, imageUrls))
                .isInstanceOf(ShelterNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findCompeletedRecruitments 메서드 실행 시")
    class FindCompletedRecruitmentsTest {

        @Test
        @DisplayName("성공")
        void findCompletedRecruitments() {
            //given
            Long volunteerId = 1L;
            PageRequest pageRequest = PageRequest.of(0, 10);
            Shelter shelter = shelter();
            Recruitment recruitment = recruitment(shelter);
            PageImpl<Recruitment> recruitmentPage = new PageImpl<>(List.of(recruitment));
            FindCompletedRecruitmentsResponse expected = FindCompletedRecruitmentsResponse.from(
                recruitmentPage);

            given(recruitmentRepository.findCompletedRecruitments(anyLong(), any()))
                .willReturn(recruitmentPage);

            //when
            FindCompletedRecruitmentsResponse recruitments = recruitmentService.findCompletedRecruitments(
                volunteerId, pageRequest);

            //then
            assertThat(recruitments).usingRecursiveComparison()
                .ignoringFields("recruitmentId")
                .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("findRecruitments 실행 시")
    class FindRecruitmentsTest {

        @Test
        @DisplayName("성공")
        void findRecruitments() {
            //give
            String keyword = "keyword";
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = LocalDate.now();
            String isClosed = "IS_CLOSED";
            KeywordCondition keywordCondition = new KeywordCondition(true, true, true);
            PageRequest pageRequest = PageRequest.of(0, 10);
            Shelter shelter = shelter();
            Recruitment recruitment = recruitment(shelter);
            PageImpl<Recruitment> recruitments = new PageImpl<>(List.of(recruitment));

            given(recruitmentRepository.findRecruitments(keyword, startDate, endDate,
                RecruitmentStatusFilter.valueOf(isClosed).getIsClosed(),
                keywordCondition, pageRequest)).willReturn(recruitments);

            //when
            FindRecruitmentsResponse recruitmentsByVolunteer
                = recruitmentService.findRecruitments(keyword, startDate, endDate,
                RecruitmentStatusFilter.valueOf(isClosed).getIsClosed(), keywordCondition,
                pageRequest);

            //then
            PageInfo pageInfo = recruitmentsByVolunteer.pageInfo();
            assertThat(pageInfo.totalElements()).isEqualTo(recruitments.getSize());
            FindRecruitmentResponse findRecruitment = recruitmentsByVolunteer.recruitments()
                .get(0);
            assertThat(findRecruitment.recruitmentTitle()).isEqualTo(recruitment.getTitle());
            assertThat(findRecruitment.recruitmentStartTime()).isEqualTo(
                recruitment.getStartTime());
            assertThat(findRecruitment.recruitmentEndTime()).isEqualTo(recruitment.getEndTime());
            assertThat(findRecruitment.recruitmentApplicantCount()).isEqualTo(
                recruitment.getApplicantCount());
            assertThat(findRecruitment.recruitmentCapacity()).isEqualTo(recruitment.getCapacity());
            assertThat(findRecruitment.shelterName()).isEqualTo(recruitment.getShelter().getName());
            assertThat(findRecruitment.shelterImageUrl())
                .isEqualTo(recruitment.getShelter().getImage());

        }
    }

    @Nested
    @DisplayName("findRecruitmentsV2 실행 시")
    class FindRecruitmentsV2Test {

        Shelter shelter;

        @BeforeEach
        void setUp() {
            shelter = ShelterFixture.shelter();
        }

        @Nested
        @DisplayName("검색 조건이 하나라도 주어진 경우")
        class WithCondition {

            String keyword;
            LocalDate startDate;
            LocalDate endDate;
            Boolean isClosed;
            boolean titleContains;
            boolean contentContains;
            boolean shelterNameContains;
            KeywordCondition keywordCondition;
            LocalDateTime createdAt;
            Long recruitmentId;

            @BeforeEach
            void setUp() {
                keyword = null;
                startDate = null;
                endDate = null;
                isClosed = null;
                titleContains = true;
                contentContains = true;
                shelterNameContains = true;
                keywordCondition = new KeywordCondition(true, true, true);
                createdAt = null;
                recruitmentId = null;
            }

            @Test
            @DisplayName("성공: db에서 조회")
            void findRecruitments() {
                //give
                startDate = LocalDate.now();
                endDate = LocalDate.now();
                PageRequest pageRequest = PageRequest.of(0, 10);
                Recruitment recruitment = recruitment(shelter);
                ReflectionTestUtils.setField(recruitment, "recruitmentId", recruitmentId);
                SliceImpl<Recruitment> recruitments = new SliceImpl<>(List.of(recruitment));

                given(recruitmentRepository.findRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition, createdAt, recruitmentId, pageRequest))
                    .willReturn(recruitments);
                given(recruitmentRepository.countFindRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition))
                    .willReturn(Long.valueOf(recruitments.getSize()));

                //when
                FindRecruitmentsResponse recruitmentsByVolunteer
                    = recruitmentService.findRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition, createdAt, recruitmentId, pageRequest);

                //then
                PageInfo pageInfo = recruitmentsByVolunteer.pageInfo();
                assertThat(pageInfo.totalElements()).isEqualTo(recruitments.getSize());
                FindRecruitmentResponse findRecruitment = recruitmentsByVolunteer.recruitments()
                    .get(0);
                assertThat(findRecruitment.recruitmentTitle()).isEqualTo(recruitment.getTitle());
                assertThat(findRecruitment.recruitmentStartTime()).isEqualTo(
                    recruitment.getStartTime());
                assertThat(findRecruitment.recruitmentEndTime()).isEqualTo(
                    recruitment.getEndTime());
                assertThat(findRecruitment.recruitmentApplicantCount()).isEqualTo(
                    recruitment.getApplicantCount());
                assertThat(findRecruitment.recruitmentCapacity()).isEqualTo(
                    recruitment.getCapacity());
                assertThat(findRecruitment.shelterName()).isEqualTo(
                    recruitment.getShelter().getName());
                assertThat(findRecruitment.shelterImageUrl())
                    .isEqualTo(recruitment.getShelter().getImage());
            }

            @Test
            @DisplayName("성공: db에서 조회")
            void findRecruitmentsWithOtherConditions() {
                //given
                keyword = "keyword";
                isClosed = true;
                keywordCondition = new KeywordCondition(true, false, false);
                PageRequest pageRequest = PageRequest.of(0, 10);
                Recruitment recruitment = recruitment(shelter);
                ReflectionTestUtils.setField(recruitment, "recruitmentId", recruitmentId);
                SliceImpl<Recruitment> recruitments = new SliceImpl<>(List.of(recruitment));

                given(recruitmentRepository.findRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition, createdAt, recruitmentId, pageRequest))
                    .willReturn(recruitments);
                given(recruitmentRepository.countFindRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition))
                    .willReturn(Long.valueOf(recruitments.getSize()));

                //when
                FindRecruitmentsResponse recruitmentsByVolunteer
                    = recruitmentService.findRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition, createdAt, recruitmentId, pageRequest);

                //then
                then(recruitmentRepository).should()
                    .findRecruitmentsV2(keyword, startDate, endDate, isClosed, keywordCondition,
                        createdAt, recruitmentId, pageRequest);
            }

            @Test
            @DisplayName("성공: db에서 조회")
            void findRecruitmentsWithAnotherCondition() {
                //given
                keyword = "keyword";
                recruitmentId = 1L;
                createdAt = LocalDateTime.now();
                PageRequest pageRequest = PageRequest.of(0, 10);
                Recruitment recruitment = recruitment(shelter);
                ReflectionTestUtils.setField(recruitment, "recruitmentId", recruitmentId);
                SliceImpl<Recruitment> recruitments = new SliceImpl<>(List.of(recruitment));

                given(recruitmentRepository.findRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition, createdAt, recruitmentId, pageRequest))
                    .willReturn(recruitments);
                given(recruitmentRepository.countFindRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition)).willReturn(Long.valueOf(recruitments.getSize()));

                //when
                FindRecruitmentsResponse recruitmentsByVolunteer
                    = recruitmentService.findRecruitmentsV2(keyword, startDate, endDate,
                    isClosed, keywordCondition, createdAt, recruitmentId, pageRequest);

                //then
                then(recruitmentRepository).should()
                    .findRecruitmentsV2(keyword, startDate, endDate, isClosed, keywordCondition,
                        createdAt, recruitmentId, pageRequest);
            }
        }

        @Nested
        @DisplayName("검색 조건이 주어지지 않은 경우")
        class WithoutCondition {

            final String nullKeyword = null;
            final LocalDate nullStartDate = null;
            final LocalDate nullEndDate = null;
            final Boolean nullIsClosed = null;
            final KeywordCondition nullKeywordCondition = null;
            final LocalDateTime nullCreatedAt = null;
            final Long nullRecruitmentId = null;

            @Test
            @DisplayName("성공: 캐시 저장소에서 조회를 수행한다.")
            void findRecruitmentsWhenCached() {
                //given
                List<Recruitment> recruitments = RecruitmentFixture.recruitments(shelter, 20);
                List<FindRecruitmentResponse> recruitmentResponses = recruitments.stream()
                    .map(FindRecruitmentResponse::from).toList();
                PageRequest pageRequest = PageRequest.of(0, 10);
                boolean hasNext = true;
                FindRecruitmentsResponse response = new FindRecruitmentsResponse(
                    recruitmentResponses,
                    PageInfo.of(recruitments.size(), hasNext));

                given(recruitmentCacheRepository.findRecruitments(pageRequest.getPageSize()))
                    .willReturn(response);

                //when
                FindRecruitmentsResponse recruitmentsV2 = recruitmentService.findRecruitmentsV2(
                    nullKeyword, nullStartDate, nullEndDate, nullIsClosed, nullKeywordCondition,
                    nullCreatedAt, nullRecruitmentId, pageRequest);

                //then
                then(recruitmentCacheRepository).should()
                    .findRecruitments(pageRequest.getPageSize());
                then(recruitmentRepository).should(times(0))
                    .findRecruitmentsV2(nullKeyword, nullStartDate, nullEndDate, nullIsClosed,
                        nullKeywordCondition, nullCreatedAt, nullRecruitmentId, pageRequest);
            }
        }
    }

    @Nested
    @DisplayName("findRecruitmentsByShelter 메서드 실행 시")
    class FindRecruitmentsByShelterTest {

        @Test
        @DisplayName("성공")
        void findRecruitmentsByShelter() {
            // given
            Shelter shelter = shelter();
            Recruitment recruitment = recruitment(shelter);
            setField(recruitment, "recruitmentId", 4L);
            Page<Recruitment> pageResult = new PageImpl<>(List.of(recruitment));
            FindRecruitmentsByShelterResponse expected = findRecruitmentsByShelterResponse(
                pageResult);

            when(recruitmentRepository.findRecruitmentsByShelterOrderByCreatedAt(
                anyLong(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(pageResult);

            // when
            FindRecruitmentsByShelterResponse result = recruitmentService.findRecruitmentsByShelter(
                anyLong(), any(), any(), any(), anyBoolean(), any(), any());

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("findShelterRecruitments 메서드 실행 시")
    class FindShelterRecruitmentsResponseTest {

        @Test
        @DisplayName("성공")
        void findShelterRecruitments() {
            // given
            Shelter shelter = shelter();
            Recruitment recruitment = recruitment(shelter);
            ReflectionTestUtils.setField(recruitment, "recruitmentId", 4L);
            Page<Recruitment> pageResult = new PageImpl<>(List.of(recruitment));
            FindShelterRecruitmentsResponse expected = FindShelterRecruitmentsResponse(
                pageResult);

            when(recruitmentRepository.findShelterRecruitments(anyLong(), any()))
                .thenReturn(pageResult);

            // when
            FindShelterRecruitmentsResponse result = recruitmentService.findShelterRecruitments(
                anyLong(), any());

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("findRecruitmentDetail 메서드 실행 시")
    class FindRecruitmentDetail {

        @Test
        @DisplayName("성공")
        void findRecruitmentDetail() {
            // given
            Shelter shelter = shelter();
            Recruitment recruitment = recruitment(shelter);
            FindRecruitmentDetailResponse expected = findRecruitmentDetailResponse(recruitment);

            when(recruitmentRepository.findRecruitmentDetail(anyLong())).thenReturn(
                Optional.of(recruitment));

            // when
            FindRecruitmentDetailResponse result = recruitmentService.findRecruitmentDetail(
                anyLong());

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("예외(RecruitmentNotFoundException): 존재하지 않는 모집글")
        void throwExceptionWhenRecruitmentIsNotExist() {
            // given
            when(recruitmentRepository.findRecruitmentDetail(anyLong())).thenReturn(
                Optional.empty());

            // when
            Exception exception = catchException(
                () -> recruitmentService.findRecruitmentDetail(anyLong()));

            // then
            assertThat(exception).isInstanceOf(RecruitmentNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("closeRecruitment 메서드 호출 시")
    class CloseRecruitmentTest {

        Recruitment recruitment;

        @BeforeEach
        void setUp() {
            Shelter shelter = shelter();
            recruitment = RecruitmentFixture.recruitment(shelter);
        }

        @Test
        @DisplayName("성공")
        void closeRecruitment() {
            //given
            given(recruitmentRepository.findByShelterIdAndRecruitmentId(anyLong(), anyLong()))
                .willReturn(Optional.ofNullable(recruitment));

            //when
            recruitmentService.closeRecruitment(1L, 1L);

            //then
            assertThat(recruitment.isClosed()).isTrue();
        }

        @Test
        @DisplayName("예외(RecruitmentNotFounException): 존재하지 않는 봉사 모집글")
        void exceptionWhenRecruitmentNotFound() {
            //given
            given(recruitmentRepository.findByShelterIdAndRecruitmentId(anyLong(), anyLong()))
                .willReturn(Optional.empty());

            //when
            Exception exception = catchException(
                () -> recruitmentService.closeRecruitment(1L, 1L));

            //then
            assertThat(exception).isInstanceOf(RecruitmentNotFoundException.class);
        }

        @Test
        @DisplayName("성공: 캐시 되어있던 봉사 모집글이 아니면 캐시 추가를 호출하지 않는다.")
        void doesNotInvokeCacheSave_WhenNotExistsInCache() {
            //given
            given(recruitmentRepository.findByShelterIdAndRecruitmentId(anyLong(), anyLong()))
                .willReturn(Optional.ofNullable(recruitment));
            given(recruitmentCacheRepository.deleteRecruitment(any()))
                .willReturn(0L);

            //when
            recruitmentService.closeRecruitment(1L, 1L);

            //then
            then(recruitmentCacheRepository).should(times(0))
                .saveRecruitment(any());
        }

        @Test
        @DisplayName("성공: 캐시 되어있던 봉사 모집글이면 캐시 추가를 호출한다.")
        void invokeCacheSave_WhenExistsInCache() {
            //given
            given(recruitmentRepository.findByShelterIdAndRecruitmentId(anyLong(), anyLong()))
                .willReturn(Optional.ofNullable(recruitment));
            given(recruitmentCacheRepository.deleteRecruitment(any()))
                .willReturn(1L);

            //when
            recruitmentService.closeRecruitment(1L, 1L);

            //then
            then(recruitmentCacheRepository).should(times(1)).
                saveRecruitment(any());
        }
    }

    @Nested
    @DisplayName("updateRecruitment 메서드 호출 시")
    class UpdateRecruitmentTest {

        Recruitment recruitment;

        @BeforeEach
        void setUp() {
            Shelter shelter = ShelterFixture.shelter();
            recruitment = RecruitmentFixture.recruitment(shelter);
        }

        @Test
        @DisplayName("성공")
        void updateRecruitment() {
            //given
            String newTitle = recruitment.getTitle() + "a";
            LocalDateTime newStartTime = recruitment.getStartTime().plusDays(1);
            LocalDateTime newEndTime = recruitment.getEndTime().plusDays(1);
            LocalDateTime newDeadline = recruitment.getDeadline().plusDays(1);
            int newCapacity = recruitment.getCapacity() + 1;
            String newContent = recruitment.getContent() + "a";
            List<String> newImageUrls = List.of("a1", "a2");
            List<String> originalImageUrls = recruitment.getImages();

            given(recruitmentRepository.findByShelterIdAndRecruitmentIdWithImages(anyLong(),
                anyLong())).willReturn(Optional.ofNullable(recruitment));

            //when
            recruitmentService.updateRecruitment(1L, 1L,
                newTitle, newStartTime, newEndTime, newDeadline, newCapacity, newContent,
                newImageUrls);

            //then
            verify(applicationEventPublisher, times(1)).publishEvent(
                new ImageDeletionEvent(originalImageUrls));

            assertThat(recruitment.getTitle()).isEqualTo(newTitle);
            assertThat(recruitment.getStartTime()).isEqualTo(newStartTime);
            assertThat(recruitment.getEndTime()).isEqualTo(newEndTime);
            assertThat(recruitment.getDeadline()).isEqualTo(newDeadline);
            assertThat(recruitment.getCapacity()).isEqualTo(newCapacity);
            assertThat(recruitment.getContent()).isEqualTo(newContent);
            assertThat(recruitment.getImages()).containsExactlyElementsOf(newImageUrls);
        }

        @Test
        @DisplayName("예외(RecruitmentNotFoundException): 존재하지 않는 봉사 모집글")
        void exceptionWhenRecruitmentNotFound() {
            //given
            String newTitle = recruitment.getTitle() + "a";
            LocalDateTime newStartTime = recruitment.getStartTime().plusDays(1);
            LocalDateTime newEndTime = recruitment.getEndTime().plusDays(1);
            LocalDateTime newDeadline = recruitment.getDeadline().plusDays(1);
            int newCapacity = recruitment.getCapacity() + 1;
            String newContent = recruitment.getContent() + "a";
            List<String> newImageUrls = List.of("a1", "a2");

            given(recruitmentRepository.findByShelterIdAndRecruitmentIdWithImages(anyLong(),
                anyLong())).willReturn(Optional.empty());

            //when
            Exception exception = catchException(() -> recruitmentService.updateRecruitment(1L, 1L,
                newTitle, newStartTime, newEndTime, newDeadline, newCapacity, newContent,
                newImageUrls));

            //then
            assertThat(exception).isInstanceOf(RecruitmentNotFoundException.class);
        }

        @Test
        @DisplayName("성공: 캐시 되어있던 봉사 모집글이 아니면 캐시 추가를 호출하지 않는다.")
        void doesNotInvokeCacheSave_WhenNotExistsInCache() {
            //given
            String newTitle = recruitment.getTitle() + "a";
            LocalDateTime newStartTime = recruitment.getStartTime().plusDays(1);
            LocalDateTime newEndTime = recruitment.getEndTime().plusDays(1);
            LocalDateTime newDeadline = recruitment.getDeadline().plusDays(1);
            int newCapacity = recruitment.getCapacity() + 1;
            String newContent = recruitment.getContent() + "a";
            List<String> newImageUrls = List.of("a1", "a2");

            given(recruitmentRepository.findByShelterIdAndRecruitmentIdWithImages(anyLong(),
                anyLong())).willReturn(Optional.ofNullable(recruitment));
            given(recruitmentCacheRepository.deleteRecruitment(any())).willReturn(0L);

            //when
            recruitmentService.updateRecruitment(1L, 1L,
                newTitle, newStartTime, newEndTime, newDeadline, newCapacity, newContent,
                newImageUrls);

            //then
            then(recruitmentCacheRepository).should(times(0)).saveRecruitment(any());
        }

        @Test
        @DisplayName("성공: 캐시 되어있던 봉사 모집글이면 캐시 추가를 호출한다.")
        void invokeCacheSave_WhenExistsInCache() {
            //given
            String newTitle = recruitment.getTitle() + "a";
            LocalDateTime newStartTime = recruitment.getStartTime().plusDays(1);
            LocalDateTime newEndTime = recruitment.getEndTime().plusDays(1);
            LocalDateTime newDeadline = recruitment.getDeadline().plusDays(1);
            int newCapacity = recruitment.getCapacity() + 1;
            String newContent = recruitment.getContent() + "a";
            List<String> newImageUrls = List.of("a1", "a2");

            given(recruitmentRepository.findByShelterIdAndRecruitmentIdWithImages(anyLong(),
                anyLong())).willReturn(Optional.ofNullable(recruitment));
            given(recruitmentCacheRepository.deleteRecruitment(any())).willReturn(1L);

            //when
            recruitmentService.updateRecruitment(1L, 1L,
                newTitle, newStartTime, newEndTime, newDeadline, newCapacity, newContent,
                newImageUrls);

            //then
            then(recruitmentCacheRepository).should(times(1)).saveRecruitment(any());
        }
    }

    @Nested
    @DisplayName("deleteRecruitment 메서드 호출 시")
    class DeleteRecruitmentTest {

        @Test
        @DisplayName("성공")
        void deleteRecruitment() {
            //given
            Shelter shelter = shelter();
            Recruitment recruitment = recruitment(shelter);

            given(recruitmentRepository.findByShelterIdAndRecruitmentId(anyLong(), anyLong()))
                .willReturn(Optional.of(recruitment));

            //when
            recruitmentService.deleteRecruitment(1L, 1L);

            //then
            then(recruitmentRepository).should().delete(any(Recruitment.class));
        }

        @Test
        @DisplayName("예외(RecruitmentNotFoundException): 존재하지 않는 봉사 모집글")
        void exceptionWhenRecruitmentNotFound() {
            //given
            given(recruitmentRepository.findByShelterIdAndRecruitmentId(anyLong(), anyLong()))
                .willReturn(Optional.empty());

            //when
            Exception exception = catchException(
                () -> recruitmentService.deleteRecruitment(1L, 1L));

            //then
            assertThat(exception).isInstanceOf(RecruitmentNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("autoCloseRecruitment 메서드 호출 시")
    class AutoCloseRecruitmentTest {

        @Test
        @DisplayName("성공: repository 호출, 캐시 repository 호출")
        void autoCloseRecruitment() {
            //given
            //when
            recruitmentService.autoCloseRecruitment();

            //then
            then(recruitmentRepository).should().closeRecruitmentsIfNeedToBe();
            then(recruitmentCacheRepository).should().closeRecruitmentsIfNeedToBe();
        }
    }
}
