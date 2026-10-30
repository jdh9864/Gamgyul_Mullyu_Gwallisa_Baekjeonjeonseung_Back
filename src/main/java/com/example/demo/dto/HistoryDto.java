package com.example.demo.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

public class HistoryDto {

    // [단건 업데이트 내용]
    @Data
    public static class UpdateRequest {
        private String problemId;
        // 추가: 백엔드에서 히스토리를 처음 생성/저장할 때 과목 정보를 넣기 위해 프론트에서 같이 넘겨줘야 함
        private String subjectType;
        private String subCategory;

        private String userDifficulty;
        private String reviewNotes;
    }

    // 클라이언트 -> 서버: 한 번에 여러 개의 오답노트/난이도를 덮어쓸 때 (List로 감쌈)
    @Data
    public static class BulkUpdateRequest {
        private List<UpdateRequest> updates;
    }

    // 서버 -> 클라이언트: 화면에 뿌려줄 때 과목/단원 정보도 같이 줌
    @Data
    public static class Response {
        private String problemId;
        private String subjectType;
        private String subCategory;
        private boolean isCorrect;
        private String userDifficulty;
        private String reviewNotes;
        private LocalDateTime solvedAt;
    }
}
