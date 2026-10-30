package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Document(collection = "UserSolveHistories")
@CompoundIndex(name = "user_problem_idx", def = "{'userId': 1, 'problemId': 1, 'solvedAt': -1}")
@Getter @Setter
public class UserSolveHistory {

    @Id
    private String id;

    private String userId;
    private String problemId;

    // [다중 필터 검색을 위한 비정규화 필드 (필수)]
    private String subjectType;   // 예: "물류관리론"
    private String subCategory;   // 예: "물류총론"

    private boolean isCorrect;

    private String userDifficulty;
    private String reviewNotes;

    private LocalDateTime solvedAt = LocalDateTime.now();
}