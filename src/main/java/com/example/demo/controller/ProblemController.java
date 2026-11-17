package com.example.demo.controller;

import com.example.demo.dto.AiDto;
import com.example.demo.dto.ProblemDto;
import com.example.demo.service.AiPublishService;
import com.example.demo.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, Object> redisTemplate;

    // 1. [최초 요청] Server A가 큐에 넣고 즉시 소켓을 끊음
    @PostMapping("/fetch")
    public ResponseEntity<?> fetchOrGenerateProblems(
            @RequestHeader("User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody AiDto.GenerateRequest request) {

        // 서비스에서 큐 2개(Stream, KV)에 다 넣고 오면 true 반환
        boolean isPublished = aiPublishService.publishGenerateTaskIfNeeded(userId, idempotencyKey, request);

        if (isPublished) {
            return ResponseEntity.accepted().body("AI 출제 진행 중입니다. 잠시 후 다시 요청해주세요.");
        } else {
            List<ProblemDto.QuestionResponse> problems = problemService.getUnsolvedProblems(userId, request);
            return ResponseEntity.ok(problems);
        }
    }

    // 2. [폴링 요청] 프론트엔드가 O(1) 룩업으로 상태만 확인
    @GetMapping("/fetch/status")
    public ResponseEntity<?> checkGenerationStatus(
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String redisStateKey = "idempotency:ai:" + idempotencyKey;
        Object statusOrResult = redisTemplate.opsForValue().get(redisStateKey);

        if (statusOrResult == null) {
            // TTL 5분이 지났거나, 해커가 아무 키나 막 찔러볼 때
            return ResponseEntity.badRequest().body("존재하지 않거나 만료된 요청입니다.");
        }

        if ("PROCESSING".equals(statusOrResult)) {
            // 아직 Server B가 Gemini 통신 중일 때
            return ResponseEntity.accepted().body("AI 출제 진행 중입니다. 잠시 후 다시 요청해주세요.");
        }

        // 🌟 Server B가 통신을 끝내고 "PROCESSING"이라는 문자열 대신
        // 찐 JSON 데이터(결과물)를 Redis 덮어씌워 둔 상태!
        return ResponseEntity.ok(statusOrResult);
    }
}
