import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const bookingLatency = new Trend('booking_latency', true);
const bookingErrors = new Rate('booking_errors');

const scenario = __ENV.SCENARIO || 'stress';
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

const allScenarios = {
	// 1. stress: 단순 정합성 검증 (동시성 100요청 격리)
	stress: {
		executor: 'shared-iterations',
		vus: 100,
		iterations: 100,
		maxDuration: '30s',
	},
	// 2. spike: 00시 정각 1000 TPS 순간 폭증 부하 테스트
	spike: {
		executor: 'ramping-arrival-rate',
		startRate: 10,
		timeUnit: '1s',
		stages: [
			{ duration: '10s', target: 10 },
			{ duration: '5s',  target: 1000 },
			{ duration: '30s', target: 1000 },
			{ duration: '10s', target: 10 },
		],
		preAllocatedVUs: 200,
		maxVUs: 2500,
	},
	// 3. idempotency: 동일 유저 짧은 간격 연속 요청(연타) 멱등성 검증 시나리오
	// 3명의 유저가 각각 1초 동안 미친 듯이 100번씩 연타(총 300요청)를 날리는 하드코어 패턴 모사
	idempotency: {
		executor: 'per-vu-iterations',
		vus: 3,
		iterations: 100,
		maxDuration: '10s',
	},
};

export const options = {
	scenarios: {
		[scenario]: allScenarios[scenario],
	},
	thresholds: {
		'booking_latency': ['p(95)<2000'],
		'booking_errors': ['rate<0.01'],
	},
};

// 2,000명의 고유 회원 풀
const USER_IDS = Array.from({ length: 2000 }, (_, i) => 1001 + i);

// 멱등성 테스트 전용 고정 데이터 키 정의
// per-vu-iterations 환경에서 VU 별로 고정된 키를 매핑하기 위함
const FIXED_KEYS = ['key-vu-0', 'key-vu-1', 'key-vu-2'];

export default function () {
    const productId = 1;
    let userId;
    let idempotencyKey;

    // 실행된 시나리오가 'idempotency'일 경우 연타 공격
    if (scenario === 'idempotency') {
        userId = 9990 + __VU; // VU 번호에 따라 9991, 9992, 9993 유저로 격리
        idempotencyKey = FIXED_KEYS[__VU - 1]; // 해당 VU가 던지는 100번의 요청은 전부 '동일한 키'로 고정
    } else {
        // 일반적인 선착순 트래픽 및 부하 시뮬레이션
        userId = USER_IDS[Math.floor(Math.random() * USER_IDS.length)];
        idempotencyKey = uuidv4();
    }

    const payload = JSON.stringify({
        productId: productId,
        customerName: `유저-${userId}`,
        customerPhone: '010-1234-5678',
        totalAmount: 159000
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-User-Id': String(userId),
            'X-Idempotency-Key': idempotencyKey,
        },
    };

    const res = http.post(`${baseUrl}/bookings`, payload, params);

    // 시나리오별 검증 바운더리 설정
    let ok;
    if (scenario === 'idempotency') {
        // 멱등성 시나리오의 경우: 100번 중 1번만 성공(201)하고, 나머지 99번은 중복 에러(409 or 422)가 나야 정상
        ok = check(res, {
            'is processed or blocked gracefully': (r) => r.status === 201 || r.status === 409 || r.status === 422,
        });
    } else {
        ok = check(res, {
            'is valid sales response': (r) => r.status === 201 || r.status === 409,
        });
    }

    bookingLatency.add(res.timings.duration);
    bookingErrors.add(!ok);
}