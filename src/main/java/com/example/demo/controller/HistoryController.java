package com.example.demo.controller;

import com.example.demo.dto.HistoryDto;
import com.example.demo.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Profile("serverB")
@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * 풀이/오답 이력 조회
     * 예: GET /api/v1/history?onlyWrong=true&subCategory=CORS%20(교차%20출처%20리소스%20공유)
     */
    @GetMapping
    public ResponseEntity<List<HistoryDto.Response>> getHistories(
            @RequestHeader("User-Id") String userId,
            @RequestParam(name = "onlyWrong", defaultValue = "false") boolean onlyWrong,
            @RequestParam(name = "subjectType", required = false) String subjectType,
            @RequestParam(name = "subCategory", required = false) String subCategory) {

        return ResponseEntity.ok(historyService.getHistories(userId, onlyWrong, subjectType, subCategory));
    }

    /**
     * 단건 오답노트 체감 난이도·복습 메모 수정/생성
     */
    @PutMapping
    public ResponseEntity<?> updateHistory(
            @RequestHeader("User-Id") String userId,
            @RequestBody HistoryDto.UpdateRequest request) {
        try {
            return ResponseEntity.ok(historyService.updateHistory(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 다건 오답노트 일괄 저장/수정
     */
    @PutMapping("/bulk")
    public ResponseEntity<List<HistoryDto.Response>> bulkUpdateHistories(
            @RequestHeader("User-Id") String userId,
            @RequestBody HistoryDto.BulkUpdateRequest request) {

        return ResponseEntity.ok(historyService.bulkUpdateHistories(userId, request));
    }
}
