package com.example.demo.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.Consumer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Profile("serverB") // ★ Server B 전용 워커
@Service
@RequiredArgsConstructor
public class AiWorkerService {

    private final RedisTemplate<String, Object> redisTemplate;
    // 나중에 주입받을 Gemini API 서비스
    // private final GeminiApiService geminiApiService;

    private static final String STREAM_KEY = "ai:generate:queue";
    private static final String CONSUMER_GROUP = "ai-worker-group";
    private static final String CONSUMER_NAME = "worker-1"; // 서버 스케일아웃 시 UUID 등으로 동적 할당 가능

    // 애플리케이션 시작 시 컨슈머 그룹 초기화 로직 (없으면 생성)
    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception e) {
            // 이미 그룹이 존재하면 발생하는 예외 무시
        }
    }

    // 2초마다 큐를 확인하여 메시지를 가져옴 (Gemini Rate Limit 방어용 폴링)
    @Scheduled(fixedDelay = 2000)
    public void consumeAiQueue() {
        try {
            // Consumer Group 방식을 사용하여 한 번에 1개의 메시지만 가져옴 (최대 1초 대기)
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(1)),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );

            if (messages == null || messages.isEmpty()) {
                return; // 큐가 비어있으면 다음 주기까지 대기
            }

            for (MapRecord<String, Object, Object> message : messages) {
                Map<Object, Object> payload = message.getValue();

                String subjectType = (String) payload.get("subjectType");
                String subCategories = (String) payload.get("subCategories");
                int count = Integer.parseInt((String) payload.get("count"));

                System.out.println("🔥 [Server B] 큐 수신 완료! 출제 시작. 과목: " + subjectType + ", 단원: " + subCategories);

                // TODO: 1. Gemini API 호출 (예전 코드 이식 예정)
                // List<Problem> newProblems = geminiApiService.generate(subjectType, subCategories, count);

                // TODO: 2. MongoDB 저장
                // problemRepository.saveAll(newProblems);

                // 3. 작업이 무사히 끝났으므로 Redis에 XACK (처리 완료) 전송
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, message.getId());
                System.out.println("✅ [Server B] 출제 완료 및 DB 저장 완료. XACK 전송.");
            }

        } catch (Exception e) {
            System.err.println("❌ [Server B] 워커 처리 중 에러 발생: " + e.getMessage());
            // 에러 시 XACK를 보내지 않으므로, 데이터는 Redis PEL(Pending Entries List)에 안전하게 남아 유실되지 않음
        }
    }
}
