package com.example.demo.controller;

import com.example.demo.dto.AiDto;
import com.example.demo.dto.ProblemDto;
import com.example.demo.service.AiPublishService;
import com.example.demo.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Profile("serverA")
@RestController
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final AiPublishService aiPublishService;
    private final ProblemService problemService;

    // 프론트엔드가 문제 출제 요청 및 폴링 시 호출하는 단일 엔드포인트
    @PostMapping("/fetch")
    public ResponseEntity<?> fetchOrGenerateProblems(
            @RequestHeader("User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody AiDto.GenerateRequest request) {

        // 1. 워터마크 검사 (DB에 문제가 충분한지 확인)
        boolean isPublished = aiPublishService.publishGenerateTaskIfNeeded(userId, idempotencyKey, request);

        if (isPublished) {
            // [분기 1] 문제가 모자라서 Redis 큐에 출제 지시를 내림
            // 프론트엔드는 202를 받으면 N초 뒤에 똑같은 이 API를 다시 호출(폴링)함
            return ResponseEntity.accepted().body("AI 출제 진행 중입니다. 잠시 후 다시 요청해주세요.");
        } else {
            // [분기 2] DB에 안 푼 문제가 충분함 -> 즉시 문제 서빙
            // TODO: ProblemService에 '해당 단원의 안 푼 문제 count 개 가져오기' 메서드 구현 필요
            List<ProblemDto.QuestionResponse> problems = problemService.getUnsolvedProblems(userId, request);

            // 프론트엔드는 200 OK와 함께 문제 배열을 받고 화면에 렌더링
            return ResponseEntity.ok(problems);
        }
    }
}
