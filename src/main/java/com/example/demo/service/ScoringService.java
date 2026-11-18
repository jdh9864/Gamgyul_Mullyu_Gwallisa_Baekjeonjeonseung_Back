package com.example.demo.service;

import com.example.demo.domain.Problem;
import com.example.demo.domain.SubjectDomain;
import com.example.demo.domain.UserSolveHistory;
import com.example.demo.dto.ScoreDto;
import com.example.demo.repository.ProblemRepository;
import com.example.demo.repository.SubjectDomainRepository;
import com.example.demo.repository.UserSolveHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("serverB")
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final ProblemRepository problemRepository;
    private final SubjectDomainRepository subjectDomainRepository;
    private final UserSolveHistoryRepository userSolveHistoryRepository;

    public ScoreDto.SubmitResponse scoreAndSaveHistory(String userId, ScoreDto.SubmitRequest request) {

        // 1. 문제 조회 (O(1) 룩업)
        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문제입니다."));

        // 도메인 정보 조회 (domainId가 null이거나 DB에 없을 경우 방어)
        SubjectDomain domain = (problem.getDomainId() != null)
                ? subjectDomainRepository.findById(problem.getDomainId()).orElse(null)
                : null;

        // 2. [오프로딩] 정답 비교 (AI가 아닌 백엔드에서 정수 비교)
        boolean isCorrect = (problem.getAnswer() == request.getUserAnswer());

        // 3. [메타데이터 자동화] 난이도 자동 스케일링
        String autoDifficulty = isCorrect ? "쉬움" : "어려움";

        // 4. 풀이 이력(UserSolveHistory) 생성 및 저장 (단일 문서 쓰기는 MongoDB 네이티브 원자성 보장)
        UserSolveHistory history = new UserSolveHistory();
        history.setUserId(userId);
        history.setProblemId(problem.getId());
        history.setSubjectType(domain != null ? domain.getSubjectType() : "테스트과목");
        history.setSubCategory(domain != null ? domain.getSubCategory() : "테스트분류");
        history.setCorrect(isCorrect);
        history.setUserDifficulty(autoDifficulty);

        userSolveHistoryRepository.save(history);

        // 5. 응답 반환
        ScoreDto.SubmitResponse response = new ScoreDto.SubmitResponse();
        response.setCorrect(isCorrect);
        response.setCorrectAnswer(problem.getAnswer());
        response.setExplanation(problem.getExplanation());

        return response;
    }
}
