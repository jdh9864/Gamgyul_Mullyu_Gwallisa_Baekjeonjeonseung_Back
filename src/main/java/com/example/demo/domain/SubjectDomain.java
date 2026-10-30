package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "SubjectDomains") // 컬렉션 이름 깔끔하게 첫글자 대문자 복수형 통일
@Getter @Setter
public class SubjectDomain {

    @Id
    private String id;

    @Field("subject_type")
    private String subjectType;

    @Field("major_category")
    private String majorCategory;

    @Field("sub_category")
    private String subCategory;

    // JSONB: 빈 배열을 기본값으로 할당하여 제약 조건 완화 반영
    @Field("core_keywords")
    private List<String> coreKeywords = new ArrayList<>();

    @Field("reference_text")
    private String referenceText;

    @Field("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}