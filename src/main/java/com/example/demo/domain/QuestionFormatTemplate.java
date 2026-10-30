package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Document(collection = "QuestionFormatTemplate") // DB 이름과 정확히 일치시킴[cite: 1]
@Getter @Setter
public class QuestionFormatTemplate {

    @Id
    private String id;

    // @Field 싹 다 제거, 수동 Getter/Setter 제거하고 Lombok 적용
    private String typeName;
    private String questionExample;
    private String answerExample;
    private LocalDateTime createdAt = LocalDateTime.now();
}
