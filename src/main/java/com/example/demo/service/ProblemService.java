package com.example.demo.service;

import com.example.demo.domain.Problem;
import com.example.demo.domain.SubjectDomain;
import com.example.demo.domain.UserSolveHistory;
import com.example.demo.dto.AiDto;
import com.example.demo.dto.ProblemDto;
import com.example.demo.repository.SubjectDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final MongoTemplate mongoTemplate;
    private final SubjectDomainRepository subjectDomainRepository;

    // 프론트엔드에 배열(List) 형태로 '안 푼 문제'를 던져주는 핵심 메서드
    public List<ProblemDto.QuestionResponse> getUnsolvedProblems(String userId, AiDto.GenerateRequest request) {

        // 1. 요청받은 과목/단원의 도메인 ID들 추출
        List<SubjectDomain> domains = subjectDomainRepository.findBySubjectTypeAndSubCategoryIn(
                request.getSubjectType(), request.getSubCategories());
        List<String> domainIds = domains.stream().map(SubjectDomain::getId).collect(Collectors.toList());

        // 2. 유저가 이미 푼 고유 문제 ID 목록 추출 (findDistinct 활용 초고속 조회)
        Query historyQuery = new Query();
        historyQuery.addCriteria(Criteria.where("userId").is(userId)
                .and("subjectType").is(request.getSubjectType())
                .and("subCategory").in(request.getSubCategories()));
        List<String> solvedProblemIds = mongoTemplate.findDistinct(historyQuery, "problemId", UserSolveHistory.class, String.class);

        // 3. 안 푼 문제 조회 로직
        // 조건: 해당 단원들(domainIds)에 속하면서 AND 유저가 푼 문제 ID 리스트(solvedProblemIds)에 없는(nin) 문제
        Query problemQuery = new Query();
        problemQuery.addCriteria(Criteria.where("domainId").in(domainIds)
                .and("id").nin(solvedProblemIds)); // nin = Not In

        // 랜덤 출제를 위해 일단 요청한 갯수의 3배수 정도를 가져옵니다.
        // (전체 풀스캔하면 메모리 터지니까 제한을 둠)
        problemQuery.limit(request.getCount() * 3);

        List<Problem> unsolvedProblems = mongoTemplate.find(problemQuery, Problem.class);

        // 4. 가져온 리스트를 무작위로 섞고(Shuffle), 정확히 요청한 갯수(count)만큼만 잘라냄
        Collections.shuffle(unsolvedProblems);
        List<Problem> selectedProblems = unsolvedProblems.stream()
                .limit(request.getCount())
                .collect(Collectors.toList());

        // 5. 프론트엔드 요구사항: 정답 가리고 '배열(List)' 형태로 반환
        return selectedProblems.stream()
                .map(ProblemDto.QuestionResponse::from)
                .collect(Collectors.toList());
    }
}
