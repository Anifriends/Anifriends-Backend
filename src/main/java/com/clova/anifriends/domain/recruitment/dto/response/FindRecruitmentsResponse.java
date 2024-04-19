package com.clova.anifriends.domain.recruitment.dto.response;

import com.clova.anifriends.domain.common.PageInfo;
import com.clova.anifriends.domain.recruitment.Recruitment;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

public record FindRecruitmentsResponse(
    List<FindRecruitmentResponse> recruitments,
    PageInfo pageInfo) {

    public record FindRecruitmentResponse(
        Long recruitmentId,
        String recruitmentTitle,
        LocalDateTime recruitmentStartTime,
        LocalDateTime recruitmentEndTime,
        LocalDateTime recruitmentDeadline,
        boolean recruitmentIsClosed,
        int recruitmentApplicantCount,
        int recruitmentCapacity,
        String shelterName,
        String shelterImageUrl,
        LocalDateTime recruitmentCreatedAt) {

        public static FindRecruitmentResponse from(Recruitment recruitment) {
            return new FindRecruitmentResponse(
                recruitment.getRecruitmentId(),
                recruitment.getTitle(),
                recruitment.getStartTime(),
                recruitment.getEndTime(),
                recruitment.getDeadline(),
                recruitment.isClosed(),
                recruitment.getApplicantCount(),
                recruitment.getCapacity(),
                recruitment.getShelter().getName(),
                recruitment.getShelter().getImage(),
                recruitment.getCreatedAt()
            );
        }
    }

    public static FindRecruitmentsResponse from(Page<Recruitment> recruitments) {
        PageInfo pageInfo = PageInfo.of(recruitments.getTotalElements(), recruitments.hasNext());
        List<FindRecruitmentResponse> content = recruitments.getContent()
            .stream()
            .map(FindRecruitmentResponse::from)
            .toList();
        return new FindRecruitmentsResponse(content, pageInfo);
    }

    public static FindRecruitmentsResponse fromV2(Slice<Recruitment> recruitments, Long count) {
        PageInfo pageInfo = PageInfo.of(count, recruitments.hasNext());
        List<FindRecruitmentResponse> content = recruitments.getContent()
            .stream()
            .map(FindRecruitmentResponse::from)
            .toList();
        return new FindRecruitmentsResponse(content, pageInfo);
    }
}
