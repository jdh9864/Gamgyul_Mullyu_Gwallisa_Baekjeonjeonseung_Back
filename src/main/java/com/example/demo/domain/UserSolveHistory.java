package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Document(collection = "UserSolveHistories") // DB 이름과 정확히 일치시킴[cite: 1]
@CompoundIndex(name = "user_problem_idx", def = "{'userId': 1, 'problemId': 1, 'solvedAt': -1}")
@Getter @Setter
public class UserSolveHistory {

    @Id
    private String id;

    private String userId;
    private String problemId;

    private String subjectType;
    private String subCategory;

    private boolean isCorrect;
    private String userDifficulty;
    private String reviewNotes;

    private LocalDateTime solvedAt = LocalDateTime.now();
}