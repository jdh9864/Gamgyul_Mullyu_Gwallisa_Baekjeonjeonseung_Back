package com.example.demo.dto;

import lombok.Data;
import java.util.List;

public class AiDto {

    @Data
    public static class GenerateRequest {
        private String subjectType;         // 예: "물류관리론"
        private List<String> subCategories; // 예: ["물류총론", "보관하역론", "물류정보"] (다중 선택)
        private int count = 5;              // 총 뽑아낼 문제 수
    }
}
