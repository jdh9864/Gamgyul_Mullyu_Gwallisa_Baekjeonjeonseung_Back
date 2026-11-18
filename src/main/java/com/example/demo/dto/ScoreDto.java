package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public class ScoreDto {

    // 클라이언트 -> 서버 (오직 문제 ID와 유저가 고른 답만)
    @Data
    public static class SubmitRequest {
        private String problemId;
        private int userAnswer; // 1~4
    }

    // 서버 -> 클라이언트 (채점 결과와 해설만 즉각 반환)
    @Data
    public static class SubmitResponse {
        @JsonProperty("isCorrect")
        private boolean correct;        // 맞았는지 (JSON 키: "isCorrect", getter: isCorrect(), setter: setCorrect())
        private int correctAnswer;      // 실제 정답
        private String explanation;     // 해설
    }
}
