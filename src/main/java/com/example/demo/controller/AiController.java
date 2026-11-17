//package com.example.demo.controller;
//
//import com.example.demo.dto.AiDto;
//import com.example.demo.service.AiPublishService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Profile;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@Profile("serverA") // ★ Server A로 구동할 때만 활성화
//@RestController
//@RequestMapping("/api/v1/ai")
//@RequiredArgsConstructor
//public class AiController {
//
//    private final AiPublishService aiPublishService;
//
//    @PostMapping("/generate")
//    public ResponseEntity<String> generateProblems(
//            @RequestHeader("User-Id") String userId, // 유저가 푼 문제 수를 계산하기 위해 필수
//            @RequestHeader("Idempotency-Key") String idempotencyKey, // 따닥 방지용 고유 키
//            @RequestBody AiDto.GenerateRequest request) {
//
//        try {
//            // DB에 안 푼 문제가 충분한지 검사 후, 모자랄 때만 Redis 큐에 퍼블리싱
//            boolean isPublished = aiPublishService.publishGenerateTaskIfNeeded(userId, idempotencyKey, request);
//
//            if (isPublished) {
//                // [분기 1] 큐에 들어감 -> AI가 문제 만드는 중
//                // 프론트엔드 액션: 202 응답을 받으면 "AI가 문제를 생성하고 있습니다(최대 30초 소요)..." 로딩 화면 띄움
//                return ResponseEntity.accepted().body("출제 요청이 큐에 등록되었습니다.");
//            } else {
//                // [분기 2] 큐에 안 들어감 -> DB에 안 푼 문제가 넉넉함
//                // 프론트엔드 액션: 200 응답을 받으면 로딩 화면 없이, 바로 일반 '문제 조회 API'를 호출해서 유저에게 문제 세트를 보여줌
//                return ResponseEntity.ok("문제가 충분합니다. 일반 문제 조회 API를 호출하세요.");
//            }
//        } catch (IllegalArgumentException e) {
//            // 중복 클릭 시 409 Conflict 반환 (멱등성 방어)
//            return ResponseEntity.status(409).body(e.getMessage());
//        }
//    }
//}
