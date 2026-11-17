package com.example.demo.controller;

import com.example.demo.dto.ScoreDto;
import com.example.demo.service.ScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("serverB")
@RestController
@RequestMapping("/api/v1/score")
@RequiredArgsConstructor
public class ScoringController {

    private final ScoringService scoringService;

    // Light DB I/O: 50ms 이내 응답을 보장하는 동기(Sync) 엔드포인트
    @PostMapping
    public ResponseEntity<ScoreDto.SubmitResponse> submitAnswer(
            @RequestHeader("User-Id") String userId,
            @RequestBody ScoreDto.SubmitRequest request) {

        ScoreDto.SubmitResponse response = scoringService.scoreAndSaveHistory(userId, request);
        return ResponseEntity.ok(response);
    }
}
