package com.clova.anifriends.domain.review.service;

import static com.clova.anifriends.domain.recruitment.support.fixture.RecruitmentFixture.recruitment;
import static com.clova.anifriends.domain.review.support.ReviewDtoFixture.findReviewResponse;
import static com.clova.anifriends.domain.review.support.ReviewFixture.review;
import static com.clova.anifriends.domain.shelter.support.ShelterFixture.shelter;
import static com.clova.anifriends.domain.volunteer.support.VolunteerFixture.volunteer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.clova.anifriends.domain.common.dto.PageInfo;
import com.clova.anifriends.domain.recruitment.Recruitment;
import com.clova.anifriends.domain.review.Review;
import com.clova.anifriends.domain.review.dto.response.FindReviewResponse;
import com.clova.anifriends.domain.review.dto.response.FindShelterReviewsResponse;
import com.clova.anifriends.domain.review.dto.response.FindVolunteerReviewsResponse;
import com.clova.anifriends.domain.review.exception.ReviewNotFoundException;
import com.clova.anifriends.domain.review.repository.ReviewRepository;
import com.clova.anifriends.domain.shelter.Shelter;
import com.clova.anifriends.domain.volunteer.Volunteer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @InjectMocks
    private ReviewService reviewService;

    @Mock
    private ReviewRepository reviewRepository;

    @Nested
    @DisplayName("findReviewById 메서드 실행 시")
    class FindReviewByIdTest {

        @Test
        @DisplayName("성공")
        void findReview() {
            //given
            Shelter shelter = shelter();
            Volunteer volunteer = volunteer();
            Recruitment recruitment = recruitment(shelter);
            Review review = review(recruitment, volunteer);
            FindReviewResponse expected = findReviewResponse(review);

            when(reviewRepository.findByReviewIdAndVolunteerId(anyLong(), anyLong()))
                .thenReturn(Optional.of(review));

            //when
            FindReviewResponse result = reviewService.findReview(anyLong(), anyLong());

            //then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("예외(NotFoundReviewException): 존재하지 않는 리뷰")
        void exceptionWhenReviewIsNotExist() {
            //given
            when(reviewRepository.findByReviewIdAndVolunteerId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

            //when
            Exception exception = catchException(
                () -> reviewService.findReview(anyLong(), anyLong()));

            //then
            assertThat(exception).isInstanceOf(ReviewNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findShelterReviews 메서드 실행 시")
    class FindShelterReviewsTest {

        @Test
        @DisplayName("성공")
        void findShelterReviews() {
            //given
            Long shelterId = 1L;
            PageRequest pageRequest = PageRequest.of(0, 10);
            Shelter shelter = shelter();
            Recruitment recruitment = recruitment(shelter);
            Volunteer volunteer = volunteer();
            Review review = review(recruitment, volunteer);
            PageImpl<Review> reviewPage = new PageImpl<>(List.of(review));
            FindShelterReviewsResponse expected = FindShelterReviewsResponse.from(reviewPage);

            given(reviewRepository.findAllByRecruitmentShelterShelterId(anyLong(), any()))
                .willReturn(reviewPage);

            //then
            FindShelterReviewsResponse response
                = reviewService.findShelterReviews(shelterId, pageRequest);

            //then
            assertThat(response).usingRecursiveComparison()
                .ignoringFields("reviewId")
                .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("findVolunteerReviews 메서드 실행 시")
    class FindVolunteerReviewsTest {

        @Test
        @DisplayName("성공")
        void findVolunteerReviews() {
            // given
            Long volunteerId = 1L;
            PageRequest pageRequest = PageRequest.of(0, 10);
            Shelter shelter = shelter();
            Recruitment recruitment = recruitment(shelter);
            Volunteer volunteer = volunteer();
            Review review = review(recruitment, volunteer);
            PageImpl<Review> reviewPage = new PageImpl<>(List.of(review));
            FindVolunteerReviewsResponse expected = FindVolunteerReviewsResponse.of(
                reviewPage.getContent(), PageInfo.from(reviewPage));

            given(reviewRepository.findAllByVolunteerVolunteerIdOrderByCreatedAtDesc(anyLong(), any()))
                .willReturn(reviewPage);

            // when
            FindVolunteerReviewsResponse response
                = reviewService.findVolunteerReviews(volunteerId, pageRequest);

            // then
            assertThat(response).usingRecursiveComparison()
                .ignoringFields("reviewId")
                .isEqualTo(expected);
        }
    }
}
