package com.example.demo.dto;

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
        private boolean isCorrect;      // 맞았는지
        private int correctAnswer;      // 실제 정답
        private String explanation;     // 해설
    }
}
