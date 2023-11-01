package com.clova.anifriends.domain.volunteer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clova.anifriends.base.BaseControllerTest;
import com.clova.anifriends.domain.applicant.wrapper.ApplicantStatus;
import com.clova.anifriends.domain.volunteer.Volunteer;
import com.clova.anifriends.domain.volunteer.dto.request.RegisterVolunteerRequest;
import com.clova.anifriends.domain.volunteer.dto.response.FindVolunteerMyPageResponse;
import com.clova.anifriends.domain.volunteer.support.VolunteerDtoFixture;
import com.clova.anifriends.domain.volunteer.support.VolunteerFixture;
import com.clova.anifriends.domain.volunteer.support.VolunteerImageFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class VolunteerControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("봉사자 회원가입 API 호출 시")
    void registerVolunteer() throws Exception {
        // given
        RegisterVolunteerRequest registerVolunteerRequest = VolunteerDtoFixture.registerVolunteerRequest();
        given(volunteerService.registerVolunteer(any())).willReturn(1L);

        // when
        ResultActions resultActions = mockMvc.perform(
            post("/api/volunteers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerVolunteerRequest)));

        // then
        resultActions.andExpect(status().isCreated())
            .andDo(restDocs.document(
                requestFields(
                    fieldWithPath("email").type(STRING).description("이메일"),
                    fieldWithPath("password").type(STRING).description("비밀번호"),
                    fieldWithPath("name").type(STRING).description("이름"),
                    fieldWithPath("birthDate").type(STRING).description("생년월일"),
                    fieldWithPath("phoneNumber").type(STRING).description("전화번호"),
                    fieldWithPath("gender").type(STRING).description("성별")
                ),
                responseHeaders(
                    headerWithName("Location").description("생성된 리소스 위치")
                )
            ));
    }

    @Test
    @DisplayName("봉사자 마이페이지 조회 API 호출 시")
    void findVolunteerMyPage() throws Exception {
        // given
        Volunteer volunteer = VolunteerFixture.volunteer();
        FindVolunteerMyPageResponse findVolunteerMyPageResponse = new FindVolunteerMyPageResponse(
            volunteer.getEmail(),
            volunteer.getName(),
            volunteer.getBirthDate(),
            volunteer.getPhoneNumber(),
            volunteer.getTemperature(),
            volunteer.getApplications().stream()
                .filter(applicant -> applicant.getStatus().equals(ApplicantStatus.ATTENDANCE))
                .count(),
            VolunteerImageFixture.volunteerImage(volunteer).getImageUrl());
        given(volunteerService.findVolunteerMyPage(anyLong())).willReturn(
            findVolunteerMyPageResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            get("/api/volunteers/me")
                .header("volunteerId", 1L)
                .contentType(MediaType.APPLICATION_JSON));

        // then
        resultActions.andExpect(status().isOk())
            .andDo(restDocs.document(
                responseFields(
                    fieldWithPath("email").type(STRING).description("이메일"),
                    fieldWithPath("name").type(STRING).description("이름"),
                    fieldWithPath("birthDate").type(STRING).description("생년월일"),
                    fieldWithPath("phoneNumber").type(STRING).description("전화번호"),
                    fieldWithPath("temperature").type(NUMBER).description("체온"),
                    fieldWithPath("volunteerCount").type(NUMBER).description("봉사 횟수"),
                    fieldWithPath("imageUrl").type(STRING).description("프로필 이미지 URL")
                )
            ));
    }
}
