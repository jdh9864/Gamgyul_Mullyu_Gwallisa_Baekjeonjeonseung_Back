package com.example.demo.service;

import com.example.demo.domain.Problem;
import com.example.demo.domain.SubjectDomain;
import com.example.demo.repository.ProblemRepository;
import com.example.demo.repository.SubjectDomainRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Profile("serverB")
@Service
@RequiredArgsConstructor
public class AiWorkerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProblemRepository problemRepository;
    private final SubjectDomainRepository subjectDomainRepository; // ★ 추가된 부분: 도메인 ID 조회를 위함
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.create();
    private final JsonParser springJsonParser = JsonParserFactory.getJsonParser();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String geminiApiUrl;

    // Server A와 약속한 큐 이름 (AI_TASK_STREAM으로 통일)
    private static final String STREAM_KEY = "AI_TASK_STREAM";
    private static final String CONSUMER_GROUP = "ai-worker-group";
    private static final String CONSUMER_NAME = "worker-1";

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception e) {
            // 이미 그룹이 존재하면 무시
        }
    }

    // 2초마다 큐를 확인하여 메시지 1개씩 순차 소비 (메모리 폭발 방지, API Rate Limit 준수)
    @Scheduled(fixedDelay = 2000)
    public void consumeAiQueue() {
        try {
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(1)),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );

            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> message : messages) {
                Map<Object, Object> streamData = message.getValue();

                String userId = (String) streamData.get("userId");
                String idempotencyKey = (String) streamData.get("idempotencyKey");
                String payloadJson = (String) streamData.get("payload");

                String redisStateKey = "idempotency:ai:" + idempotencyKey;

                log.info("🔥 [Server B] 큐 수신 완료! 출제 시작. User: {}", userId);

                try {
                    // 1. JSON Payload 파싱
                    @SuppressWarnings("unchecked")
                    Map<String, Object> requestMap = objectMapper.readValue(payloadJson, Map.class);
                    String subjectType = (String) requestMap.get("subjectType");

                    // ★ 수정된 부분: 단원명 리스트를 가져와 실제 DB의 도메인 ID를 찾습니다.
                    @SuppressWarnings("unchecked")
                    List<String> subCategoryList = (List<String>) requestMap.get("subCategories");
                    int count = (Integer) requestMap.getOrDefault("count", 1);

                    List<SubjectDomain> domains = subjectDomainRepository.findBySubjectTypeAndSubCategoryIn(subjectType, subCategoryList);
                    String realDomainId = domains.isEmpty() ? "UNKNOWN_DOMAIN" : domains.get(0).getId();

                    // 2. 프롬프트 조립
                    String prompt = String.format(
                            "당신은 정보처리기사 및 IT 기술 면접 전문 출제 위원입니다. 다음 '출제 카테고리 및 주제'를 엄격히 반영하여 새로운 객관식(4지 선다) 문제를 %d개 출제하세요.\n" +
                                    "문제는 주니어 개발자가 반드시 알아야 할 핵심 개념, 원리, 장단점 등을 묻는 실무적인 내용이어야 합니다.\n" +
                                    "반드시 아래의 JSON 배열 포맷으로만 응답하며 마크다운이나 부가 설명은 절대 추가하지 마세요.\n\n" +
                                    "출력 포맷: [{\"content\": \"문제 지문 내용\", \"choices\": [\"보기1\", \"보기2\", \"보기3\", \"보기4\"], \"answer\": 1, \"explanation\": \"정답 해설\"}]\n\n" +
                                    "[출제 카테고리]: %s\n[출제 주제]: %s",
                            count, subjectType, subCategoryList.toString()
                    );

                    // 3. Gemini API 호출
                    Map<String, Object> geminiPayload = Map.of("contents", Map.of("parts", Map.of("text", prompt)));

                    String rawAiResponse = webClient.post()
                            .uri(geminiApiUrl + "?key=" + geminiApiKey)
                            .header("Content-Type", "application/json")
                            .bodyValue(geminiPayload)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    // 4. 응답 파싱 및 방어 로직 (★ 수정된 부분: 배열 문자열 대신 진짜 도메인 ID를 넘김)
                    List<Problem> generatedProblems = parseGeminiResponse(rawAiResponse, realDomainId);

                    // 5. MongoDB 저장
                    List<Problem> savedProblems = problemRepository.saveAll(generatedProblems);

                    // 5-1. (추가) 히스토리 큐에 할당 이벤트를 쏴줍니다. (Server C용 비동기 할당)
                    try {
                        List<String> newProblemIds = savedProblems.stream().map(Problem::getId).collect(Collectors.toList());
                        Map<String, String> historyEvent = Map.of(
                                "userId", userId,
                                "problemIds", objectMapper.writeValueAsString(newProblemIds)
                        );
                        redisTemplate.opsForStream().add("HISTORY_TASK_STREAM", historyEvent);
                    } catch (Exception e) {
                        log.warn("히스토리 큐 발행 실패 (할당 지연 가능성 있음)", e);
                    }

                    // 6. 상태 폴링 키를 찐 결과(JSON 배열)로 덮어쓰기
                    String resultJson = objectMapper.writeValueAsString(savedProblems);
                    redisTemplate.opsForValue().set(redisStateKey, resultJson, 5, TimeUnit.MINUTES);

                    // 7. XACK 전송 (작업 완벽 종료)
                    redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, message.getId());
                    log.info("✅ [Server B] 출제 완료 및 DB 저장 완료. XACK 전송.");

                } catch (Exception processEx) {
                    log.error("❌ [Server B] 단위 작업 실패. Key: {}", idempotencyKey, processEx);
                    redisTemplate.opsForValue().set(redisStateKey, "ERROR: AI 문제 생성 실패", 5, TimeUnit.MINUTES);
                }
            }

        } catch (Exception e) {
            log.error("❌ [Server B] Redis 큐 읽기 실패: {}", e.getMessage());
        }
    }

    // AI 응답 텍스트를 파싱하여 Problem 엔티티 리스트로 변환하는 방어 로직
    private List<Problem> parseGeminiResponse(String rawAiResponse, String realDomainId) {
        List<Problem> problemList = new ArrayList<>();

        try {
            Map<String, Object> rootMap = springJsonParser.parseMap(rawAiResponse);
            List<?> candidates = (List<?>) rootMap.get("candidates");
            Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> contentMap = (Map<?, ?>) firstCandidate.get("content");
            List<?> parts = (List<?>) contentMap.get("parts");
            Map<?, ?> textPart = (Map<?, ?>) parts.get(0);
            String aiGeneratedText = (String) textPart.get("text");

            String cleanJson = aiGeneratedText.replaceAll("```json", "").replaceAll("```", "").trim();
            List<Object> parsedList = springJsonParser.parseList(cleanJson);

            for (Object obj : parsedList) {
                Map<String, Object> pMap = (Map<String, Object>) obj;
                Problem p = new Problem();

                // ★ 수정된 부분: 진짜 도메인 ID가 세팅됩니다.
                p.setDomainId(realDomainId);
                p.setContent((String) pMap.getOrDefault("content", "지문 생성 실패"));

                Object choicesObj = pMap.get("choices");
                if (choicesObj instanceof List) {
                    List<String> choices = new ArrayList<>();
                    for (Object c : (List<?>) choicesObj) {
                        choices.add(String.valueOf(c));
                    }
                    p.setChoices(choices);
                }

                Object answerObj = pMap.get("answer");
                if (answerObj instanceof Number) {
                    p.setAnswer(((Number) answerObj).intValue());
                } else {
                    p.setAnswer(1);
                }

                p.setExplanation((String) pMap.getOrDefault("explanation", "해설 없음"));

                problemList.add(p);
            }
        } catch (Exception e) {
            log.error("AI 응답 JSON 파싱 실패. Raw Text: {}", rawAiResponse, e);
            throw new RuntimeException("AI 응답을 Problem 엔티티로 변환하는데 실패했습니다.");
        }

        return problemList;
    }
}