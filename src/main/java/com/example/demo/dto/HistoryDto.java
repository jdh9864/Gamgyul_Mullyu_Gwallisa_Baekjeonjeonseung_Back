package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

public class HistoryDto {

    // [단건 업데이트 내용]
    @Data
    public static class UpdateRequest {
        private String problemId;
        private String subjectType;
        private String subCategory;
        private String userDifficulty;
        private String reviewNotes;
    }

    // 클라이언트 -> 서버: 한 번에 여러 개의 오답노트/난이도를 덮어쓸 때
    @Data
    public static class BulkUpdateRequest {
        private List<UpdateRequest> updates;
    }

    // 서버 -> 클라이언트: 오답노트 카드 렌더링에 필요한 이력 + 문제 원문 결합 응답
    @Data
    public static class Response {
        private String historyId;
        private String problemId;
        private String subjectType;
        private String subCategory;

        @JsonProperty("isCorrect")
        private boolean correct;        // JSON 키: "isCorrect", getter: isCorrect(), setter: setCorrect()

        private String userDifficulty;
        private String reviewNotes;
        private LocalDateTime solvedAt;

        // Problem 컬렉션 일괄($in) 조인 필드
        private String content;
        private List<String> choices;
        private Integer correctAnswer;
        private String explanation;
    }
}
