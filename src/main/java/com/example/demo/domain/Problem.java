package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Document(collection = "Problems") // 컬렉션 이름 깔끔하게 대문자 복수형 통일
@Getter @Setter
public class Problem {

    @Id
    private String id;

    // 물류관리사 세부 영역 (예: "물류관리론", "화물운송론" 등의 ID)
    @Field("domain_id")
    private String domainId;

    // [문제 본문]
    private String content;

    // [4지선다 보기 배열] JSON 배열로 텍스트 4개가 들어감
    private List<String> choices;

    // [정답 번호] (1, 2, 3, 4) - 백엔드 오프로딩 채점 시 정수 비교용
    private int answer;

    // [AI 해설] 틀렸을 때 보여줄 설명
    private String explanation;
}
