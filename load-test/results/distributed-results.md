## 1. 테스트 환경

- **구성**: app1 + app2 + nginx (round-robin) + MySQL 8 + Redis 7-alpine (모두 Docker Compose)
- **nginx 접근 포트**: `localhost:8080` (k6 BASE_URL)
- **HikariCP**: 기본값 (maximum-pool-size=10)
- **Tomcat 스레드**: 기본값 (threads.max=200)
- **데이터 시드**: productId=1, Redis `stock:product:1` 시나리오별 적절히 세팅
- **k6 클라이언트**: 호스트 머신에서 실행 (Docker 외부)

---

## 2. Checkout API — 단일 vs 분산 비교

| 시나리오 | 항목 | 단일 인스턴스 | 분산 (app1+app2) | 변화 |
|---|---|---|---|---|
| smoke (10 TPS, 30s) | 처리 TPS | 10.0 | 10.0 | 동일 |
| | p95 latency | 8.9ms | 10.4ms | +17% (네트워크 오버헤드) |
| baseline (50 TPS, 2m) | 처리 TPS | 49.2 | 50.0 | 동일 |
| | p95 latency | 3.8ms | 6.5ms | +71% (네트워크 오버헤드) |
| **spike (50→1000→50)** | 처리 TPS | 562 | **563** | 비슷 |
| | **p95 latency** | **5.6ms** | **3.75ms** | **-33% 추가 개선** |
| | dropped | 0 | 0 | 둘 다 OK |
| **peak (1000 TPS × 1m)** | **처리 TPS** | **2758** | **1000** | **목표 달성** ⭐ |
| | **p95 latency** | **2758ms** | **3.33ms** | **-99.9% 단축** ⭐ |
| | **dropped** | **5369** | **0** | **완전 해소** |

### 핵심 발견 — peak에서 분산 효과 극대화

단일 인스턴스에서는 1000 TPS 지속을 못 견디고 p95 2.7초로 폭증, dropped 5369건이 발생. **분산 환경에선 1000 TPS를 p95 3.3ms로 안정적으로 처리**했고 dropped도 0건.

이는 **과제 요구사항인 "피크 500~1000 TPS" 처리 능력을 분산 환경에서 100% 만족**함을 의미

### Checkout 캐시 정합성도 분산 환경에서 유지

- 모든 시나리오에서 check 100% 통과 (status 200, has product, product is OPEN)
- Redis 캐시(`checkout:product:1`)가 두 인스턴스에서 공유되어 동일한 응답 보장

### 작은 부하에서는 네트워크 오버헤드가 보임

- smoke/baseline에서 분산이 단일보다 약간 느림 (nginx + Docker 네트워크)
- 부하가 작을 땐 분산의 이점이 오버헤드보다 작음 — 자연스러운 현상

---

## 3. Booking API — 단일 vs 분산 비교

| 시나리오 | 항목 | 단일 (결제 통합 후) | 분산 (app1+app2) | 변화 |
|---|---|---|---|---|
| stress (100→10) | 처리 TPS | 232 | 113 | -51% |
| | p95 latency | 415ms | 873ms | +110% (작은 부하 오버헤드) |
| | 정합성 | bookings 10건 | bookings 10건 ✅ | 유지 |
| **spike (50→1000→50)** | **처리 TPS** | **454** | **685** | **+51%** |
| | **p95 latency (전체)** | **5,213ms** | **22.8ms** | **-99.6%** ⭐ |
| | p95 latency (성공만) | - | 469ms | (성공 응답은 469ms) |
| | **dropped** | **11,487** | **18** | **-99.8%** ⭐ |
| | 정합성 | OK | OK | 유지 |
| idempotency (300→3) | 처리 TPS | 478 | 241 | -50% |
| | p95 latency | 3.8ms | 11.4ms | +200% (오버헤드) |
| | 정합성 | bookings 3건 | bookings 3건 ✅ | 유지 |

### 핵심 발견 — spike에서 폭발적 개선

- **p95 5.2초 → 22.8ms (99.6% 단축)**: 단일 인스턴스의 가장 큰 약점이었던 spike 부하가 분산으로 해결됨
- **dropped 11,487건 → 18건 (99.8% 감소)**: 서버 처리량이 한계를 거의 벗어남
- **처리 TPS 454 → 685**: 1.5배 개선. 분산의 정직한 효과
- **`expected_response:true` p95 469ms**: 성공한 booking 응답의 진짜 latency. PG 호출 + 결제 + DB INSERT 비용 포함

### 분산에서도 정합성 유지

- stress 100요청 → bookings 정확히 10건 (초과판매 0)
- idempotency 300요청 (같은 키 100×3) → bookings 정확히 3건 (중복 0)
- Redis Lua + MySQL UNIQUE의 동시성 보장이 두 인스턴스 환경에서도 그대로 동작

### 작은 부하 시나리오에서 분산이 더 느림

- stress: 100요청을 인스턴스 둘에 나누는 오버헤드 > 처리 가속 효과
- idempotency: 같은 키 따닥이라 1차 멱등(booking 조회)에서 끝남. 분산의 효과 없이 오버헤드만 추가
- **이건 분산 환경 자체의 한계가 아니라 측정 시나리오의 특성** — 작은 부하에선 단일이 더 빠르고, 큰 부하에선 분산이 절대 우위

---

## 4. 결론

### 성공한 것 ✅

- **과제의 1000 TPS 피크 요구사항 충족**: Checkout peak에서 1000 TPS × 1분 완벽 처리 (p95 3.3ms, dropped 0)
- **Booking spike에서 단일 대비 100배 이상 latency 개선**: 5.2초 → 22.8ms
- **정합성은 모든 분산 시나리오에서 100% 유지**: bookings 10건, 3건 정확히 일치
- **Redis와 MySQL을 공유 자원으로 두니 인스턴스 수 늘어도 일관성 보장**

### 한계

- 작은 부하(stress, idempotency, smoke, baseline)에서는 nginx + Docker 네트워크 오버헤드로 분산이 단일보다 약간 느림
- Booking spike의 성공 응답 p95는 여전히 469ms — PG 호출 + 결제 트랜잭션 비용. 추가 튜닝 여지 있음