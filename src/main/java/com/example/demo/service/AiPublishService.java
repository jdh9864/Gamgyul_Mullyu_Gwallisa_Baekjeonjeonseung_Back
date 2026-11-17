package com.example.demo.service;

import com.example.demo.domain.Problem;
import com.example.demo.domain.SubjectDomain;
import com.example.demo.domain.UserSolveHistory;
import com.example.demo.dto.AiDto;
import com.example.demo.repository.SubjectDomainRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPublishService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MongoTemplate mongoTemplate; // ★ 직접 카운트를 위한 템플릿
    private final SubjectDomainRepository subjectDomainRepository; // ★ 도메인 ID 조회를 위한 레포지토리
    private final ObjectMapper objectMapper;

    private static final String AI_TASK_STREAM = "AI_TASK_STREAM";

    public boolean publishGenerateTaskIfNeeded(String userId, String idempotencyKey, AiDto.GenerateRequest request) {

        // =================================================================
        // [Step 1] 동적 워터마크 검사 (DB 확인)
        // =================================================================

        // 1-1. 유저가 이미 푼 문제 ID 목록 추출 (History 테이블 사용)
        Query historyQuery = new Query();
        historyQuery.addCriteria(Criteria.where("userId").is(userId)
                .and("subjectType").is(request.getSubjectType())
                .and("subCategory").in(request.getSubCategories()));
        List<String> solvedProblemIds = mongoTemplate.findDistinct(historyQuery, "problemId", UserSolveHistory.class, String.class);

        // 1-2. 요청받은 과목/단원의 도메인 ID들 추출
        List<SubjectDomain> domains = subjectDomainRepository.findBySubjectTypeAndSubCategoryIn(
                request.getSubjectType(), request.getSubCategories());
        List<String> domainIds = domains.stream().map(SubjectDomain::getId).collect(Collectors.toList());

        // 1-3. 안 푼 문제 갯수 카운트
        Query problemQuery = new Query();
        problemQuery.addCriteria(Criteria.where("domainId").in(domainIds)
                .and("id").nin(solvedProblemIds)); // 푼 문제(nin) 제외

        long unsolvedCount = mongoTemplate.count(problemQuery, Problem.class);


        // 프론트엔드가 요구하는 문제 수(count)보다 남은 문제가 많으면 출제 큐에 넣을 필요 없음!
        if (unsolvedCount >= request.getCount()) {
            log.info("유저 [{}] - 안 푼 문제 충분함 (남은 수: {}). AI 출제 스킵.", userId, unsolvedCount);
            return false; // 컨트롤러에게 "바로 DB에서 꺼내서 줘라(200 OK)" 라고 알림
        }

        log.info("유저 [{}] - 안 푼 문제 부족함 (남은 수: {}). AI 출제 큐 적재 시작.", userId, unsolvedCount);

        // =================================================================
        // [Step 2] 문제가 모자라니 Redis 큐에 적재 (Idempotency + Stream)
        // =================================================================
        String redisStateKey = "idempotency:ai:" + idempotencyKey;

        // [Queue 1] 프론트엔드 폴링용 상태 저장소에 밀어넣기 (따닥 방지)
        Boolean isAbsent = redisTemplate.opsForValue().setIfAbsent(redisStateKey, "PROCESSING", 5, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(isAbsent)) {
            log.info("중복된 AI 출제 요청 무시: {}", idempotencyKey);
            return true; // 이미 출제 중이니까 202를 반환해서 폴링하게 냅둠
        }

        // [Queue 2] Server B가 소비할 작업 큐에 밀어넣기
        try {
            String payloadJson = objectMapper.writeValueAsString(request);

            Map<String, String> streamData = new HashMap<>();
            streamData.put("userId", userId);
            streamData.put("idempotencyKey", idempotencyKey);
            streamData.put("payload", payloadJson);

            redisTemplate.opsForStream().add(AI_TASK_STREAM, streamData);
            log.info("Redis Stream [AI_TASK_STREAM] 에 작업 발행 완료. IdempotencyKey: {}", idempotencyKey);

        } catch (Exception e) {
            redisTemplate.delete(redisStateKey); // 롤백
            log.error("AI 출제 스트림 발행 실패", e);
            throw new RuntimeException("AI 출제 요청 중 오류가 발생했습니다.");
        }

        return true; // 무사히 큐에 넣었으니 컨트롤러에게 202 반환하라고 알림
    }
}
