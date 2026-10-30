package com.example.demo.dto;

import com.example.demo.domain.Problem;
import lombok.Data;
import java.util.List;

public class ProblemDto {

    // 프론트엔드 반환용 (정답과 해설은 무조건 제외하여 클라이언트 치팅 방지)
    @Data
    public static class QuestionResponse {
        private String id;
        private String domainId;
        private String content;
        private List<String> choices;

        // Entity -> DTO 변환 편의 메서드
        public static QuestionResponse from(Problem problem) {
            QuestionResponse dto = new QuestionResponse();
            dto.setId(problem.getId());
            dto.setDomainId(problem.getDomainId());
            dto.setContent(problem.getContent());
            dto.setChoices(problem.getChoices());
            return dto;
        }
    }
}
