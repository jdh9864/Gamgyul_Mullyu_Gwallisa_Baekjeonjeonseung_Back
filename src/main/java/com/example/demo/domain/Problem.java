package com.example.demo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Document(collection = "problem") // DB 이름과 정확히 일치시킴
@Getter @Setter
public class Problem {

    @Id
    private String id;
    private String domainId;
    private String content;
    private List<String> choices;
    private int answer;
    private String explanation;
}