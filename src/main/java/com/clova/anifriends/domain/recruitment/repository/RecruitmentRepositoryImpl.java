package com.clova.anifriends.domain.recruitment.repository;

import static com.clova.anifriends.domain.recruitment.QRecruitment.recruitment;

import com.clova.anifriends.domain.recruitment.Recruitment;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecruitmentRepositoryImpl implements RecruitmentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Recruitment> findRecruitmentsByShelterOrderByCreatedAt(long shelterId,
        String keyword, LocalDate startDate, LocalDate endDate, boolean content, boolean title,
        Pageable pageable) {

        Predicate predicate = recruitment.shelter.shelterId.eq(shelterId)
            .and(getDateCondition(startDate, endDate))
            .and(getKeywordCondition(keyword, content, title));

        return queryFactory.selectFrom(recruitment)
            .where(predicate)
            .orderBy(recruitment.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
    }

    Predicate getDateCondition(LocalDate startDate, LocalDate endDate) {
        BooleanExpression predicate = recruitment.isNotNull();
        if (startDate != null) {
            predicate = predicate.and(recruitment.info.startTime.goe(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            predicate = predicate.and(recruitment.info.startTime.loe(endDate.atStartOfDay()));
        }
        return predicate;
    }

    Predicate getKeywordCondition(String keyword, boolean content, boolean title) {
        BooleanExpression predicate = recruitment.isNotNull();
        if (keyword == null || keyword.isBlank()) {
            return predicate;
        }
        if (content) {
            predicate = predicate.or(recruitment.content.content.contains(keyword));
        }
        if (title) {
            predicate = predicate.or(recruitment.title.title.contains(keyword));
        }
        return predicate;
    }
}
