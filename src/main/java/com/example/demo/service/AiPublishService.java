package com.example.demo.service;

import com.example.demo.domain.SubjectDomain;
import com.example.demo.domain.UserSolveHistory;
import com.example.demo.dto.AiDto;
import com.example.demo.repository.ProblemRepository;
import com.example.demo.repository.SubjectDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Profile("serverA")
@Service
@RequiredArgsConstructor
public class AiPublishService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final SubjectDomainRepository subjectDomainRepository;
    private final ProblemRepository problemRepository;

    private static final String STREAM_KEY = "ai:generate:queue";

    public boolean publishGenerateTaskIfNeeded(String userId, String idempotencyKey, AiDto.GenerateRequest request) {

        System.out.println("\n=== 🔍 [워터마크 디버깅 시작] ===");
        System.out.println("요청 데이터 - 과목: " + request.getSubjectType() + ", 단원: " + request.getSubCategories());

        // 1. [조회] 해당 단원들의 도메인 ID 추출
        List<SubjectDomain> domains = subjectDomainRepository.findBySubjectTypeAndSubCategoryIn(
                request.getSubjectType(), request.getSubCategories());
        List<String> domainIds = domains.stream().map(SubjectDomain::getId).collect(Collectors.toList());

        System.out.println("1. 찾아온 도메인 ID 리스트: " + domainIds);

        // 2. [계산] 전체 문제 수 (Total) 카운트
        long totalProblems = domainIds.isEmpty() ? 0 : problemRepository.countByDomainIdIn(domainIds);
        System.out.println("2. 해당 도메인들의 전체 DB 문제 수: " + totalProblems);

        // 3. [계산] 유저가 해당 단원들에서 푼 '중복 제거된' 문제 수 카운트
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId)
                .and("subjectType").is(request.getSubjectType())
                .and("subCategory").in(request.getSubCategories()));

        List<String> solvedUniqueProblemIds = mongoTemplate.findDistinct(query, "problemId", UserSolveHistory.class, String.class);
        long solvedCount = solvedUniqueProblemIds.size();
        System.out.println("3. 유저가 이미 푼 문제 수: " + solvedCount);

        // ★ 4. [핵심 방어선] 동적 워터마크 프리페칭 조건 검사 ★
        long remainingProblems = totalProblems - solvedCount;
        System.out.println("4. 남은 문제 수: " + remainingProblems + " (요청한 갯수: " + request.getCount() + ")");

        if (remainingProblems >= request.getCount()) {
            System.out.println("✅ [Server A] DB에 남은 문제가 충분하여 출제를 생략합니다. (과금 방어 성공)");
            System.out.println("================================\n");
            return false; // 큐 발행 안 함 (거짓 반환)
        }

        System.out.println("⚠️ [Server A] 문제가 고갈되어 AI 출제를 요청합니다. 큐 발행 진입!");
        System.out.println("================================\n");

        // 5. 멱등성 검증 (따닥 방지)
        String redisIdempotencyKey = "idempotency:ai:" + idempotencyKey;
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisIdempotencyKey, "PROCESSING", Duration.ofMinutes(5));

        if (Boolean.FALSE.equals(isFirstRequest)) {
            throw new IllegalArgumentException("이미 처리 중인 출제 요청입니다. 잠시 후 확인해주세요.");
        }

        // 6. Redis Stream 퍼블리싱
        Map<String, String> payload = new HashMap<>();
        payload.put("subjectType", request.getSubjectType());
        payload.put("subCategories", String.join(",", request.getSubCategories()));
        payload.put("count", String.valueOf(request.getCount()));

        StringRecord record = StreamRecords.string(payload).withStreamKey(STREAM_KEY);
        RecordId recordId = redisTemplate.opsForStream().add(record);

        System.out.println("🚀 [Server A] 큐 발행 완료. Record ID: " + recordId);
        return true;
    }
}
