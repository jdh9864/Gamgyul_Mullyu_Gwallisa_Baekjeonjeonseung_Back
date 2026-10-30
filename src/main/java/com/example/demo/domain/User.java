package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

@Document(collection = "users") // DB 이름과 정확히 일치시킴[cite: 1]
@Getter @Setter
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;
    private String password;
    private String name;

    private String lastAttendanceDate;
    private int consecutiveAttendanceDays;
}
