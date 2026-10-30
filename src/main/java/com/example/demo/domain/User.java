package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

@Document(collection = "Users")
@Getter @Setter
public class User {
    @Id
    private String id;

    @Indexed(unique = true)
    private String email;
    private String password;
    private String name;

    // [출석 체크 필드]
    private String lastAttendanceDate; // 예: "2026-10-30"
    private int consecutiveAttendanceDays; // 연속 출석 횟수
}
