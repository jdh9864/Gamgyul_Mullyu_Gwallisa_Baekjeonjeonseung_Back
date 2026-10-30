package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "subjectDomain") // DB 이름과 정확히 일치시킴
@Getter @Setter
public class SubjectDomain {

    @Id
    private String id;

    // @Field 싹 다 제거 -> 자바 변수명(카멜케이스) 그대로 DB에 들어감
    private String subjectType;
    private String majorCategory;
    private String subCategory;
    private List<String> coreKeywords = new ArrayList<>();
    private String referenceText;
    private LocalDateTime createdAt = LocalDateTime.now();
}