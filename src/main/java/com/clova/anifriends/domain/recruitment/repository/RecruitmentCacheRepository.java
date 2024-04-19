package com.clova.anifriends.domain.recruitment.repository;

import com.clova.anifriends.domain.recruitment.Recruitment;
import com.clova.anifriends.domain.recruitment.dto.response.FindRecruitmentsResponse;

public interface RecruitmentCacheRepository {

    long getTotalNumberOfRecruitments();

    void saveRecruitment(Recruitment recruitment);

    long deleteRecruitment(Recruitment recruitment);

    FindRecruitmentsResponse findRecruitments(int size);

    void closeRecruitmentsIfNeedToBe();
}
