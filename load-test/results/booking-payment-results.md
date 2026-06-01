# Booking + Payment 통합 부하 테스트 결과

## 1. 테스트 환경

- **인스턴스 구성**: 단일 로컬 인스턴스 (Spring Boot 1대 + MySQL 8 + Redis 7-Alpine)
- **대상 API**: `POST /bookings` (카드 + 포인트 복합 결제)
- **HikariCP**: 기본값 (maximum-pool-size=10)
- **Tomcat 스레드**: 기본값 (threads.max=200)
- **구현 스펙**: Redis Lua 재고 차감 + 3-tier 멱등 + Composite Payment (Strategy + Saga LIFO 보상) + B-3 트랜잭션 분리
- **사전 조건**: productId=1, 재고 10개 (`redis-cli SET stock:product:1 10`)

---

## 2. 측정 결과 — 결제 통합 전후 비교

| 시나리오 | 항목 | 결제 없음 (Day 2) | 결제 포함 (Day 3) | 변화 |
|---|---|---|---|---|
| **stress** (동시 100요청, 재고 10) | TPS | 234 | 232 | -1% |
| | p95 latency | 415ms | 415ms | 동일 |
| | DB bookings 결과 | 정확히 10건 | 정확히 10건 | ✅ 정합성 유지 |
| **idempotency** (같은 키 100회 × 3) | TPS | 627 | 478 | -24% |
| | p95 latency | 4.9ms | 3.8ms | -22% |
| | DB bookings 결과 | 정확히 3건 | 정확히 3건 | ✅ 정합성 유지 |
| **spike** (50→1000→50, 1분) | 평균 TPS | 684 | 454 | **-34%** |
| | p95 latency | 50.6ms | **5,213ms** | **+10,200%** |
| | dropped iterations | 43 | **11,487** | **+267배** |
| | DB bookings 결과 | 정합성 OK | 정확히 10건 | ✅ 정합성 유지 |

### 핵심 발견

1. **정합성은 모든 시나리오에서 완벽 보장** — 결제 통합 후에도 초과 판매 0건, 중복 booking 0건
2. **stress / idempotency는 영향 미미** — 부하가 작아서 결제 통합의 부담이 드러나지 않음
3. **spike에서 처리량 -34%, p95 100배 폭증** — 결제 통합으로 단일 인스턴스 한계가 명확히 드러남
4. **`is valid sales response` check 전건 통과** — 비즈니스적으로 의도된 SOLD_OUT 응답까지 정상 처리로 분류

---

## 3. spike 시나리오 심층 분석

### 측정값 재정리
- 1분 30초 동안 총 26,187 요청 처리, 11,487건 dropped
- 실제 처리 TPS 454 (목표 1000 TPS의 45%)
- p50 3,756ms, p95 5,213ms, p99 추정 6,000ms+, max 6,720ms
- HTTP 4xx 응답 99.96% (대부분 SOLD_OUT, 의도된 동작)
- `http_req_duration{expected_response:true}`: p95 329ms (성공 응답만)

### 결제 통합 후 요청당 부하 증가

booking 1건당 처리 비용 비교:

| | 결제 없음 (Day 2) | 결제 포함 (Day 3) |
|---|---|---|
| Redis 연산 | DECR 1회 | DECR 1회 |
| MySQL INSERT | bookings 1건 | bookings 1 + payments 2 + payment_histories 2 + point_transactions 1 = **6건** |
| 외부 호출 | 0 | PG 호출 1~2회 (MockPgClient) |
| 트랜잭션 경계 | 1개 | 2개 (PointBalanceAdapter, BookingPersistenceService) |

요청당 DB 작업이 6배 늘어났고, 트랜잭션도 분리되어 커밋 시점이 늘어났음.

### dropped 11,487건의 의미

`dropped_iterations`는 **k6가 부하를 생성하려 했으나 서버가 받아들이지 못해 클라이언트 측에서 버린 양**. 즉:
- 목표: 50→1000 TPS spike
- 실제 처리: 454 TPS
- 차이: 서버가 한계 도달 → k6가 버린 요청 (11,487건)

서버 처리량이 목표의 절반 미만으로 떨어진 신호.

### 병목 원인 추론

p95 5초는 단순 처리 지연이 아니라 **요청 대기 큐 정체**의 결과:
1. 1000 TPS 동시 in-flight 요청 → Tomcat 200 스레드 모두 점유
2. 각 스레드가 결제 트랜잭션 동안 HikariCP 커넥션을 길게 점유 (트랜잭션 분리에도 불구하고 영속화 트랜잭션 + PointBalanceAdapter 트랜잭션 = 커넥션 2회 점유)
3. HikariCP 풀(기본 10개) 고갈 → 후속 요청 대기
4. 대기 큐가 길어지면서 p95 폭증

---

## 4. 발견된 비효율 — 검증 순서

### 현재 흐름의 문제

```
BookingService.createBooking() 진입 후:
  1. findExistingBooking() → MySQL 멱등키 조회
  2. existsById(userId) → MySQL 유저 존재 조회
  3. getProductAndValidatePrice() → MySQL 상품 조회
  4. reserveStock() → Redis Lua (재고 차감)  ← 990건은 여기서 거절됨
  5. processPayment() → PG 호출 + 포인트 차감
  6. persistBookingAndPayments() → DB INSERT
```

문제점: spike에서 1000 요청 중 990건이 SOLD_OUT으로 거절될 운명인데, **990 × 3 = 2,970개의 불필요한 DB 조회**가 발생.

### 개선 가설

검증 순서를 "가벼운 것 먼저"로 재배치:
```
  1. 멱등 락 (Redis SETNX)            ← 1ms
  2. 멱등 1차 (booking 조회)           ← PK 인덱스, 빠름
  3. 재고 차감 (Redis Lua)             ← 990건은 여기서 거절
  4. 사용자/상품 검증 (DB)              ← 통과한 10건만
  5. 결제 처리
  6. 영속화
```

기대 효과:
- 990건의 DB 조회 → 0건으로 감소
- DB 커넥션 점유 시간 단축 → HikariCP 풀 회전율 향상
- **다만 p95가 50ms 수준으로 복귀하진 않을 것**. 결제 통합 자체의 비용은 그대로이므로.

---

## 5. 결론 및 다음 단계

### 결론

- **결제 통합은 정확히 동작**. 모든 시나리오에서 정합성# Booking + Payment 통합 부하 테스트 결과

## 1. 테스트 환경

- **인스턴스 구성**: 단일 로컬 인스턴스 (Spring Boot 1대 + MySQL 8 + Redis 7-Alpine)
- **대상 API**: `POST /bookings` (카드 + 포인트 복합 결제)
- **HikariCP**: 기본값 (maximum-pool-size=10)
- **Tomcat 스레드**: 기본값 (threads.max=200)
- **구현 스펙**: Redis Lua 재고 차감 + 3-tier 멱등 + Composite Payment (Strategy + Saga LIFO 보상) + B-3 트랜잭션 분리
- **사전 조건**: productId=1, 재고 10개 (`redis-cli SET stock:product:1 10`)

---

## 2. 측정 결과 — 결제 통합 전후 비교

| 시나리오 | 항목 | 결제 없음 (Day 2) | 결제 포함 (Day 3) | 변화 |
|---|---|---|---|---|
| **stress** (동시 100요청, 재고 10) | TPS | 234 | 232 | -1% |
| | p95 latency | 415ms | 415ms | 동일 |
| | DB bookings 결과 | 정확히 10건 | 정확히 10건 | ✅ 정합성 유지 |
| **idempotency** (같은 키 100회 × 3) | TPS | 627 | 478 | -24% |
| | p95 latency | 4.9ms | 3.8ms | -22% |
| | DB bookings 결과 | 정확히 3건 | 정확히 3건 | ✅ 정합성 유지 |
| **spike** (50→1000→50, 1분) | 평균 TPS | 684 | 454 | **-34%** |
| | p95 latency | 50.6ms | **5,213ms** | **+10,200%** |
| | dropped iterations | 43 | **11,487** | **+267배** |
| | DB bookings 결과 | 정합성 OK | 정확히 10건 | ✅ 정합성 유지 |

### 핵심 발견

1. **정합성은 모든 시나리오에서 완벽 보장** — 결제 통합 후에도 초과 판매 0건, 중복 booking 0건
2. **stress / idempotency는 영향 미미** — 부하가 작아서 결제 통합의 부담이 드러나지 않음
3. **spike에서 처리량 -34%, p95 100배 폭증** — 결제 통합으로 단일 인스턴스 한계가 명확히 드러남
4. **`is valid sales response` check 전건 통과** — 비즈니스적으로 의도된 SOLD_OUT 응답까지 정상 처리로 분류

---

## 3. spike 시나리오 심층 분석

### 측정값 재정리
- 1분 30초 동안 총 26,187 요청 처리, 11,487건 dropped
- 실제 처리 TPS 454 (목표 1000 TPS의 45%)
- p50 3,756ms, p95 5,213ms, p99 추정 6,000ms+, max 6,720ms
- HTTP 4xx 응답 99.96% (대부분 SOLD_OUT, 의도된 동작)
- `http_req_duration{expected_response:true}`: p95 329ms (성공 응답만)

### 결제 통합 후 요청당 부하 증가

booking 1건당 처리 비용 비교:

| | 결제 없음 (Day 2) | 결제 포함 (Day 3) |
|---|---|---|
| Redis 연산 | DECR 1회 | DECR 1회 |
| MySQL INSERT | bookings 1건 | bookings 1 + payments 2 + payment_histories 2 + point_transactions 1 = **6건** |
| 외부 호출 | 0 | PG 호출 1~2회 (MockPgClient) |
| 트랜잭션 경계 | 1개 | 2개 (PointBalanceAdapter, BookingPersistenceService) |

요청당 DB 작업이 6배 늘어났고, 트랜잭션도 분리되어 커밋 시점이 늘어났음.

### dropped 11,487건의 의미

`dropped_iterations`는 **k6가 부하를 생성하려 했으나 서버가 받아들이지 못해 클라이언트 측에서 버린 양**. 즉:
- 목표: 50→1000 TPS spike
- 실제 처리: 454 TPS
- 차이: 서버가 한계 도달 → k6가 버린 요청 (11,487건)

서버 처리량이 목표의 절반 미만으로 떨어진 신호.

### 병목 원인 추론

p95 5초는 단순 처리 지연이 아니라 **요청 대기 큐 정체**의 결과:
1. 1000 TPS 동시 in-flight 요청 → Tomcat 200 스레드 모두 점유
2. 각 스레드가 결제 트랜잭션 동안 HikariCP 커넥션을 길게 점유 (트랜잭션 분리에도 불구하고 영속화 트랜잭션 + PointBalanceAdapter 트랜잭션 = 커넥션 2회 점유)
3. HikariCP 풀(기본 10개) 고갈 → 후속 요청 대기
4. 대기 큐가 길어지면서 p95 폭증

---

## 4. 발견된 비효율 — 검증 순서

### 현재 흐름의 문제

```
BookingService.createBooking() 진입 후:
  1. findExistingBooking() → MySQL 멱등키 조회
  2. existsById(userId) → MySQL 유저 존재 조회
  3. getProductAndValidatePrice() → MySQL 상품 조회
  4. reserveStock() → Redis Lua (재고 차감)  ← 990건은 여기서 거절됨
  5. processPayment() → PG 호출 + 포인트 차감
  6. persistBookingAndPayments() → DB INSERT
```

문제점: spike에서 1000 요청 중 990건이 SOLD_OUT으로 거절될 운명인데, **990 × 3 = 2,970개의 불필요한 DB 조회**가 발생.

### 개선 가설

검증 순서를 "가벼운 것 먼저"로 재배치:
```
  1. 멱등 락 (Redis SETNX)            ← 1ms
  2. 멱등 1차 (booking 조회)           ← PK 인덱스, 빠름
  3. 재고 차감 (Redis Lua)             ← 990건은 여기서 거절
  4. 사용자/상품 검증 (DB)              ← 통과한 10건만
  5. 결제 처리
  6. 영속화
```

기대 효과:
- 990건의 DB 조회 → 0건으로 감소
- DB 커넥션 점유 시간 단축 → HikariCP 풀 회전율 향상
- **다만 p95가 50ms 수준으로 복귀하진 않을 것**. 결제 통합 자체의 비용은 그대로이므로.

---

## 5. 결론 및 다음 단계

### 결론

- **결제 통합은 정확히 동작**. 모든 시나리오에서 정합성 100% 유지
- **단일 인스턴스 + 기본 풀 설정으로는 1000 TPS spike 처리 불가**가 측정으로 확인됨
- 처리량 한계의 원인은 (a) 요청당 DB 작업 6배 증가, (b) 검증 순서의 비효율, (c) HikariCP/Tomcat 기본값의 부족 — 세 요인의 복합

### 개선 로드맵

| 우선순위 | 작업 | 예상 효과 | 작업량 |
|---|---|---|---|
| 1 | **검증 순서 최적화** (재고 차감 우선) | dropped 감소, SOLD_OUT 응답 빨라짐 | 1~2시간 |
| 2 | **Day 4 분산 환경** (Docker Compose, app1/app2, nginx) | 처리량 분산, 처리 TPS 약 2배 기대 | 반나절~1일 |
| 3 | **HikariCP / Tomcat 풀 튜닝** | p95 회복 (측정 기반 결정) | Day 5 |
| 4 | **MockPgClient 시나리오 + Saga e2e 측정** | 보상 흐름 검증 | 시간 남으면 |

다음 측정에서 비교 기준: 위 개선 후 같은 spike 시나리오 재측정 → 처리 TPS / p95 / dropped 변화 기록.
100% 유지
- **단일 인스턴스 + 기본 풀 설정으로는 1000 TPS spike 처리 불가**가 측정으로 확인됨
- 처리량 한계의 원인은 (a) 요청당 DB 작업 6배 증가, (b) 검증 순서의 비효율, (c) HikariCP/Tomcat 기본값의 부족 — 세 요인의 복합

### 개선 로드맵

| 우선순위 | 작업 | 예상 효과 | 작업량 |
|---|---|---|---|
| 1 | **검증 순서 최적화** (재고 차감 우선) | dropped 감소, SOLD_OUT 응답 빨라짐 | 1~2시간 |
| 2 | **Day 4 분산 환경** (Docker Compose, app1/app2, nginx) | 처리량 분산, 처리 TPS 약 2배 기대 | 반나절~1일 |
| 3 | **HikariCP / Tomcat 풀 튜닝** | p95 회복 (측정 기반 결정) | Day 5 |
| 4 | **MockPgClient 시나리오 + Saga e2e 측정** | 보상 흐름 검증 | 시간 남으면 |

다음 측정에서 비교 기준: 위 개선 후 같은 spike 시나리오 재측정 → 처리 TPS / p95 / dropped 변화 기록.
