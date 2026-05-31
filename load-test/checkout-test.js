import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';
import {textSummary} from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const checkoutLatency = new Trend('checkout_latency', true);
const checkoutErrors = new Rate('checkout_errors');
const checkoutSuccess = new Counter('checkout_success');

const scenario = __ENV.SCENARIO || 'smoke';
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

// 시나리오 정의
const scenarios = {
    // 1. SMOKE: 빠른 동작 확인 (10 TPS × 30초)
    // "되긴 하는가" 확인용. 매 측정 전 1차 검증.
    smoke: {
        executor: 'constant-arrival-rate',
        rate: 10,
        timeUnit: '1s',
        duration: '30s',
        preAllocatedVUs: 10,
        maxVUs: 30,
    },
    // 2. BASELINE: 평시 부하 검증 (50 TPS × 2분)
    // 과제의 "평시 50 TPS" 요건. 안정적으로 처리되는지.
    baseline: {
        executor: 'constant-arrival-rate',
        rate: 50,
        timeUnit: '1s',
        duration: '2m',
        preAllocatedVUs: 30,
        maxVUs: 100,
    },
    // 3. SPIKE: 00시 폭증 시뮬레이션 (50 → 1000 → 50, 핵심 시나리오)
    // 과제의 "순간적인 500~1000 TPS" 요건.
    // 평시 트래픽 중 5초 만에 1000 TPS로 급상승하고 1분간 유지.
    spike: {
        executor: 'ramping-arrival-rate',
        startRate: 50,
        timeUnit: '1s',
        stages: [
            {duration: '30s', target: 50},    // 평시
            {duration: '5s', target: 1000},  // 00시 폭증
            {duration: '1m', target: 1000},  // 1분간 피크 유지
            {duration: '10s', target: 50},    // 복귀
            {duration: '20s', target: 50},    // 안정화
        ],
        preAllocatedVUs: 100,
        maxVUs: 2000,
    },
    // 4. PEAK_SUSTAINED: 피크 한계 검증 (1000 TPS × 1분 균일)
    // "spike는 견디는데 지속 가능한가" 확인.
    peak: {
        executor: 'constant-arrival-rate',
        rate: __ENV.PEAK_RATE ? parseInt(__ENV.PEAK_RATE) : 1000,
        timeUnit: '1s',
        duration: '1m',
        preAllocatedVUs: 200,
        maxVUs: 2000,
    },
};

export const options = {
    scenarios: {[scenario]: scenarios[scenario]},
    thresholds: {
        'checkout_latency': ['p(95)<1000', 'p(99)<2000'],
        'checkout_errors': ['rate<0.01'],
        'http_req_failed': ['rate<0.05'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

// 1000 TPS 환경을 모사하기 위한 2,000명의 대규모 가상 유저 풀 생성
const USER_IDS = Array.from({length: 2000}, (_, i) => 1001 + i);

export default function () {
    const productId = 1;
    const userId = USER_IDS[Math.floor(Math.random() * USER_IDS.length)];

    const res = http.get(`${baseUrl}/checkout?productId=${productId}`, {
        headers: {'X-User-Id': String(userId)},
        tags: {name: 'GET /checkout'},
    });

    const ok = check(res, {
        'status is 200': (r) => r.status === 200,
        'has product': (r) => r.json('product.productId') === productId,
        'product is open': (r) => r.json('product.status') === 'OPEN',
    });

    checkoutLatency.add(res.timings.duration);
    checkoutErrors.add(!ok);
    if (ok) checkoutSuccess.add(1);
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data),
    };
}