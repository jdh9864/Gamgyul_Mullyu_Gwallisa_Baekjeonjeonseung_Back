import http from 'k6/http';
import { sleep, check } from 'k6';

// 1. 1시간짜리 하드코어 카오스 부하 시나리오 (최대 5,000명)
export const options = {
    stages: [
        // Phase 1: 스파이크 웜업 (빠르게 500명까지 멱살 잡기)
        { duration: '3m', target: 500 },
        { duration: '5m', target: 500 },

        // Phase 2: 퀀텀 점프 1차 (1000명 달성 후 10분 유지 - Server B 한계 테스트)
        { duration: '2m', target: 1000 },
        { duration: '10m', target: 1000 },

        // Phase 3: 서서히 목 조르기 (초당 약 1~2명씩 증가시키며 2000명 도달)
        { duration: '15m', target: 2000 },

        // Phase 4: 카오스 구간 (3000명 ~ 5000명 널뛰기 - Server A 스레드/TCP 고갈 유도)
        { duration: '5m', target: 3000 },
        { duration: '10m', target: 5000 }, // 여기서 Server A마저 터질 확률 99%
        { duration: '5m', target: 3000 },

        // Phase 5: 쿨다운 및 찌꺼기 회수
        { duration: '5m', target: 0 },
    ],
    timeout: '60s', // 5000명일 땐 타임아웃을 짧게 줘서 커넥션을 강제로 끊고 에러를 유발
};

export default function () {
    const uniqueId = `${__VU}_${__ITER}_${Date.now()}`;
    const userId = `load_user_${uniqueId}`;
    const idempotencyKey = `idem_${uniqueId}`;

    // [변수 생성] 악의적인 사용자 모사: 페이로드 크기를 무작위로 부풀림
    const isMalicious = Math.random() < 0.05; // 5% 확률로 거대 문자열 전송 (메모리 타격용)
    let junkData = "";
    if (isMalicious) {
        // 약 50KB짜리 쓰레기 텍스트를 만들어 Server A의 JSON 파싱 메모리를 공격
        junkData = "A".repeat(50000);
    }

    const headersA = {
        'Content-Type': 'application/json',
        'User-Id': userId,
        'Idempotency-Key': idempotencyKey,
    };

    // ========================================================
    // [STEP 1] 혼합 워크로드 분기
    // ========================================================
    // 50% 즉시 반환(Server A 방어용), 45% 정상 AI 출제(Server B 압사), 5% 악성 봇
    const scenarioToken = Math.random();
    let problemId = null;
    let isAiDone = false;
    let retries = 0;

    if (scenarioToken < 0.5) {
        // [시나리오 A: 가짜 즉시 반환 - Server A의 순수 라우팅 능력 측정]
        problemId = `dummy_existing_problem_${uniqueId}`;
        check(null, { '🎯 Step 1-A: Existing (Simulated)': () => true });
        isAiDone = true;

    } else {
        // [시나리오 B & C: AI 큐 진입]
        const fetchPayload = JSON.stringify({
            subjectType: "물류관리론",
            subCategories: ["물류총론"],
            count: 4,
            junk: junkData // 일반 유저는 빈 문자열, 악성 유저는 50KB 쓰레기값
        });

        let fetchRes = http.post('http://localhost:8080/api/v1/problems/fetch', fetchPayload, { headers: headersA });

        // 503(Service Unavailable)이나 500이 뜬다면 Server A가 드디어 무너진 것
        check(fetchRes, {
            '✅ Step 1-B: AI Queue 202': (r) => r.status === 202,
            '💥 Server A Crashed (500/503) on Trigger': (r) => r.status >= 500
        });

        if (fetchRes.status !== 202) return;

        // [STEP 2] 광기의 폴링 (사용자 연타 모사)
        const isImpatient = Math.random() < 0.1; // 10%의 유저는 1초마다 새로고침 연타
        const pollingInterval = isImpatient ? 1 : 3;
        const maxRetries = isImpatient ? 120 : 40; // 최대 대기시간 120초

        while (!isAiDone && retries < maxRetries) {
            sleep(pollingInterval);

            let statusRes = http.get('http://localhost:8080/api/v1/problems/fetch/status', { headers: headersA });

            if (statusRes.status === 200) {
                isAiDone = true;
                try {
                    let problems = statusRes.json();
                    if (problems && problems.length > 0) {
                        problemId = problems[0].id;
                    }
                } catch (e) {}
                check(statusRes, { '🎯 Step 2: AI Polling Success': (r) => r.status === 200 });
            } else if (statusRes.status === 202) {
                retries++;
            } else {
                check(statusRes, { '💥 Server A Crashed (500/503) on Polling': (r) => r.status >= 500 });
                break;
            }
        }

        if (!isAiDone) {
            check(retries, { '⚠️ Timeout: AI Worker Too Slow': (r) => r >= maxRetries });
            return;
        }
    }

    // ========================================================
    // [STEP 3] 유저 체류 시간 (Think Time)
    // ========================================================
    if (isAiDone && problemId) {
        // 문제 풀이 시간: 5000명 VUs가 동시에 연결을 맺고 대기하면서 커넥션 자원을 갉아먹음
        sleep(Math.floor(Math.random() * 30) + 15); // 15~45초 대기

        // ========================================================
        // [STEP 4] 채점 로직 타격
        // ========================================================
        const scorePayload = JSON.stringify({
            problemId: problemId,
            userAnswer: Math.floor(Math.random() * 4) + 1 // 1~4 무작위 픽
        });

        const headersB = {
            'Content-Type': 'application/json',
            'User-Id': userId,
        };

        let scoreRes = http.post('http://localhost:8081/api/v1/score', scorePayload, { headers: headersB });

        check(scoreRes, {
            '🏆 Step 4: Scoring Attempted': (r) => r.status === 200 || r.status === 500,
            '💀 Server B Crashed (503/Timeout)': (r) => r.status === 503 || r.status === 0
        });
    }
}