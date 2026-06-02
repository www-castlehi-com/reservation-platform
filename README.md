# 선착순 한정 숙박 예약 시스템 (Booking & Payment Platform)

본 시스템은 특정 시점(예: 00시 정각)에 트래픽이 폭증하는 한정 수량 특가 숙박 상품을 안정적이고 공정하게 판매할 수 있도록 설계된 고성능 백엔드 플랫폼입니다.

---

## 1. 시스템 아키텍처

본 시스템은 고가용성과 수평 확장을 고려하여 분산 인프라 환경으로 구성되어 있습니다. `nginx`를 리버스 프록시 및 로드 밸런서로 전면에 배치하고, 복수의 애플리케이션 인스턴스가 동일한 공유 자원(`Redis`, `MySQL`)을 바라보는 구조를 취하고 있습니다.

```
                  [ Client / 부하 테스트 ]
                           │
                           ▼
                    nginx (Port 8080)
                    /             \
         app1 (Port 8081)    app2 (Port 8082)  <-- Spring Boot
                    \             /
              ┌─────────────────────────┐
              │  공유 자원 (Shared)     │
              │  - Redis 7 (In-Memory)  │
              │  - MySQL 8 (RDBMS)      │
              └─────────────────────────┘
```

### 아키텍처 핵심 요약
- **로드 밸런서 (`nginx`)**: 클라이언트의 요청을 `app1`과 `app2`에 균등하게 분산 전달하며, 클라이언트의 실 IP 및 필요한 헤더 정보를 애플리케이션에 정상적으로 전달합니다.
- **분산 캐시 및 락 저장소 (`Redis`)**: 실시간 재고 조회 역할, 주문서 조회 성능 개선을 위한 캐시 보관소, 그리고 동시 진입을 제한하기 위한 분산 멱등성 락 관리자 역할을 통합 수행합니다.
- **영속성 저장소 (`MySQL`)**: 최종 확정된 예약 정보, 결제 수단별 세부 내역, 포인트 거래 이력 등을 안전하게 보관합니다. 성능 확보를 위해 테이블 간 물리적인 외래 키(FK)는 제거하고 논리적 참조와 적절한 인덱스 설계만을 취하고 있습니다.

---

## 2. 추가 인프라 구성 및 실행 방법

본 시스템은 로컬 환경에서 복잡한 설정 없이 즉시 분산 환경을 구동하고 검증할 수 있도록 Docker와 Docker Compose 기반으로 인프라가 패키징되어 있습니다.

### 사전 요구사항
시스템을 정상적으로 실행하기 위해서는 실행 대상 머신에 아래의 인프라 도구가 설치되어 있어야 합니다.
1. **Docker**: 컨테이너 기반 가상화 플랫폼 (20.10.x 이상 권장)
2. **Docker Compose**: 멀티 컨테이너 정의 및 실행 도구 (v2.x 이상 권장)

### 인프라 기동 및 애플리케이션 실행
프로젝트 루트 디렉토리에서 아래 명령어를 실행하면, 멀티 스테이지 빌드를 통해 애플리케이션 소스 코드를 빌드하고 인프라 컨테이너들과 함께 분산 네트워크 환경으로 자동 구동합니다.

```bash
# 전체 서비스 빌드 및 백그라운드 실행
docker compose up -d --build
```

실행 시 기동되는 서비스 목록은 다음과 같습니다:
- **`mysql` (Port 3306)**: 영속 데이터 저장소
- **`redis` (Port 6379)**: 실시간 재고, 멱등 락 및 캐시 보관소
- **`app1` (Port 8081)**: 첫 번째 스프링 부트 인스턴스
- **`app2` (Port 8082)**: 두 번째 스프링 부트 인스턴스
- **`nginx` (Port 8080)**: 라운드 로빈 로드 밸런서

```bash
# 구동 상태 확인
docker compose ps
```

### 초기 데이터 시딩 (Data Seeding)
애플리케이션이 최초 기동될 때, 분산 환경에서 안전하게 기동될 수 있도록 설계된 `ProductDataInitializer`가 작동합니다.
- **분산 락 제어**: 두 대의 애플리케이션(`app1`, `app2`)이 동시에 기동되더라도 Redis 분산 락(`lock:data_seed`)을 활용하여 단 한 번만 데이터베이스 시딩 작업이 수행되도록 제어합니다.
- **시드 데이터 내역**:
  - **방 타입 메타 (`room_types`)**: 스위트룸 정보를 1건 생성합니다.
  - **한정 판매 상품 (`products`)**: 판매가 159,000원, 초기 재고 10개인 당일 숙박 상품을 1건 생성합니다.
  - **사용자 지갑 (`user_wallets`)**: ID 1001번부터 3000번까지 총 2,000명의 사용자 지갑을 생성하고, 각 지갑에 초기 포인트 **50,000원**을 충전합니다.
  - **실시간 재고 캐시 웜업**: Redis 키 `stock:product:1`에 초기 재고 값 **10**을 동기화하여 웜업을 수행합니다. (Redis는 인메모리 저장소이므로 휘발 가능성을 배려하여 애플리케이션 기동 시 항상 이 웜업 단계를 거치도록 안전하게 구현되어 있습니다.)

---

## 3. API 명세

본 시스템은 트래픽 진입 경로를 최소화하고 병목을 줄이기 위해 직관적인 2개의 핵심 API를 제공합니다. 모든 요청에는 게이트웨이 인증을 거쳤음을 가정하고 사용자 식별을 위한 `X-User-Id` 헤더가 필수로 포함되어야 합니다.

### [API 1] 주문서 진입 (`GET /checkout`)
사용자가 결제 화면에 진입할 때 호출되는 API입니다. 단 한 번의 호출로 화면 구성에 필요한 상품 정보, 사용자의 실시간 잔여 포인트, 지원하는 결제 수단 목록을 통합하여 반환합니다.

#### Request
```http
GET /checkout?productId=1 HTTP/1.1
Host: localhost:8080
X-User-Id: 1001
```

- **Query Parameters**:
  - `productId` (Long, 필수): 조회할 상품의 고유 ID
- **Headers**:
  - `X-User-Id` (Long, 필수): 요청을 보내는 사용자의 고유 식별자

#### Response (200 OK)
```json
{
  "product": {
    "productId": 1,
    "title": "프리미어 스위트룸",
    "originalPrice": 600000,
    "price": 159000,
    "checkInTime": "2026-12-24T15:00:00",
    "checkOutTime": "2026-12-25T12:00:00",
    "openAt": "2026-06-01T22:00:00",
    "remainingStock": 10,
    "status": "OPEN"
  },
  "userWallet": {
    "userId": 1001,
    "pointBalance": 50000
  },
  "supportedPaymentTypes": ["CREDIT_CARD", "Y_PAY", "Y_POINT"]
}
```

- **`product.status` 값 정의**:
  - `UPCOMING`: 상품 오픈 시각(`openAt`)이 현재 시간보다 미래인 경우 (오픈 예정)
  - `OPEN`: 상품이 오픈되었고 실시간 재고가 1개 이상 남아있는 경우 (예약 가능)
  - `SOLD_OUT`: 실시간 재고가 0 이하로 소진된 경우 (매진)

#### 에러 응답 코드
| HTTP 상태코드 | 에러 코드 (`errorCode`) | 발생 상황 |
| :--- | :--- | :--- |
| 400 | `INVALID_USER` | `X-User-Id` 헤더 누락 또는 해당 사용자가 데이터베이스에 존재하지 않는 경우 |
| 404 | `PRODUCT_NOT_FOUND` | 전달된 `productId`에 해당하는 상품 정보가 존재하지 않는 경우 |

---

### [API 2] 예약 생성 및 복합 결제 (`POST /bookings`)
실시간 재고 차감과 복합 결제(포인트 + 외부 결제 등 최대 2개 수단) 처리를 단일 요청 내에서 트랜잭셔널하게 완수하는 API입니다. 

#### Request
```http
POST /bookings HTTP/1.1
Host: localhost:8080
X-User-Id: 1001
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "productId": 1,
  "payment": {
    "totalAmount": 159000,
    "methods": [
      {
        "type": "CREDIT_CARD",
        "amount": 149000,
        "cardToken": "tok_test_secure"
      },
      {
        "type": "Y_POINT",
        "amount": 10000
      }
    ]
  },
  "customerName": "홍길동",
  "customerPhone": "010-1234-5678"
}
```

- **Headers**:
  - `X-User-Id` (Long, 필수): 예약 및 결제를 시도하는 사용자 ID
  - `X-Idempotency-Key` (String, 필수): 네트워크 재시도 시 중복 결제를 차단하기 위한 고유한 멱등성 키 (UUID 포맷 권장)
- **JSON Body**:
  - `productId` (Long, 필수): 예약하려는 상품 ID
  - `payment.totalAmount` (Long, 필수): 총 결제 필요 금액 (양수)
  - `payment.methods` (Array, 필수): 실제 사용되는 결제 수단 상세 배열 (최대 2개 조합 가능)
    - `type` (Enum, 필수): `CREDIT_CARD` (신용카드), `Y_PAY` (간편결제), `Y_POINT` (포인트)
    - `amount` (Long, 필수): 해당 결제 수단으로 분할 처리할 금액
    - `cardToken` (String): `CREDIT_CARD` 사용 시 카드사를 식별하는 토큰 정보
    - `ypayToken` (String): `Y_PAY` 사용 시 간편결제 인증 토큰 정보
  - `customerName` (String, 필수): 투숙객 성명 (공백 불가)
  - `customerPhone` (String, 필수): 연락처 (공백 불가)

#### 결제 조합 및 검증 규칙
안정적인 동기식 복합 결제를 위해 내부 유효성 검증 엔진(`PaymentMethodValidator`)이 아래 규칙을 철저히 검사합니다:
1. **수단 개수**: 결제 수단은 최소 1개에서 최대 2개까지만 허용됩니다.
2. **중복 금지**: 동일한 타입의 결제 수단이 중복으로 전달되는 것을 금지합니다.
3. **외부 채널 혼용 금지**: 외부 연동이 필요한 채널(`CREDIT_CARD`, `Y_PAY`)을 동시에 혼용하여 결제할 수 없습니다. (단일 PG 트랜잭션 보장을 위함)
4. **금액 일치**: 제공된 각 결제 수단의 `amount` 합산 금액이 요청 본문의 `totalAmount`와 일치해야 합니다.
5. **허용되는 유효 조합**:
   - `CREDIT_CARD` 단독 결제
   - `Y_PAY` 단독 결제
   - `Y_POINT` 단독 결제
   - `CREDIT_CARD` + `Y_POINT` (복합 결제)
   - `Y_PAY` + `Y_POINT` (복합 결제)

#### Response (200 OK)
```json
{
  "bookingId": 5,
  "bookingNumber": "B20261224-43821",
  "status": "CONFIRMED",
  "totalAmount": 159000,
  "createdAt": "2026-06-01T22:30:15.123"
}
```

#### 에러 응답 코드
| HTTP 상태코드 | 에러 코드 (`errorCode`) | 발생 상황 |
| :--- | :--- | :--- |
| 400 | `BAD_REQUEST` | 본문 필수 필드 누락, 유효하지 않은 결제 수단 조합 등 요청 검증에 실패한 경우 |
| 400 | `INVALID_USER` | `X-User-Id` 헤더가 유효하지 않거나 일치하는 사용자가 없는 경우 |
| 402 | `INSUFFICIENT_POINT` | 사용자의 포인트 잔액이 복합 결제에서 요청한 포인트 결제 금액보다 부족한 경우 |
| 402 | `PAYMENT_FAILED` | PG사 연동 카드 한도 초과 등 외부 결제 수단 승인이 거절된 경우 (Saga 보상 처리가 수행됨) |
| 404 | `PRODUCT_NOT_FOUND` | 요청된 `productId`에 해당하는 상품이 없는 경우 |
| 409 | `SOLD_OUT` | 실시간 재고가 이미 소진되어 처리가 불가한 경우 |
| 409 | `IDEMPOTENCY_CONFLICT` | 동일한 `X-Idempotency-Key`로 현재 백엔드 내부에서 선행 프로세스가 진행 중인 경우 (중복 처리 방지) |
| 409 | `DUPLICATE_BOOKING` | 데이터베이스 영속화 시점에 고유 멱등 키 UNIQUE 제약이 충돌한 경우 (최종 방어) |
| 422 | `PRICE_MISMATCH` | 요청 본문의 `totalAmount`가 실제 데이터베이스에 등록된 상품 가격과 불일치하는 경우 |

---

## 4. 핵심 비즈니스 흐름 (FLOW)

본 시스템은 동시성 경합, 부분 장애 전파, 그리고 네트워크 불안정성 상황에서도 데이터 무결성을 일관되게 유지하기 위해 설계된 정교한 흐름을 따릅니다.

### 1. 주문서 조회 흐름 (`GET /checkout`)
조회 병목을 제거하기 위해 상품 메타 정보는 Redis 캐시를 조회하는 Look-aside 패턴을 따르고, 민감한 개인 잔액 정보 및 실시간 재고 정보는 다이렉트 조회하여 조합합니다.

```
Client ──► nginx ──► app (Round-Robin)
                       │
                       ▼
                CheckoutService
                       │
             ┌──────────┼──────────┐
             ▼          ▼          ▼
           Redis      MySQL      MySQL
        (캐시 hit?) (UserWallet) (RedisStock)
             │
         ┌───┴──┐
        miss   hit
         │      │
         ▼      └──────────────────┐
     MySQL (Product+RoomType)      │
         │                         │
     Redis SET (TTL 5초)            │
         │                         │
         ├─────────────────────────┘
         ▼
     CachedProductInfo 반환
```

---

### 2. 예약 및 결제 성공 흐름 (`POST /bookings`)
정합성이 검증된 인프라 위에서 외부 I/O와 데이터베이스 트랜잭션을 철저하게 격리하여 고성능을 발휘하도록 최적화된 성공 시나리오입니다.

```
Client ──► nginx ──► BookingController ──► BookingService
                                                 │
                                                 ▼
                               ┌─ 1. [멱등 1차] BookingRepository.findByIdempotencyKey()
                               │     └─ 이미 저장된 예약 발견 시: 기존 결과 즉시 응답 반환
                               │
                               ├─ 2. [멱등 2차] IdempotencyLockManager.acquire()
                               │     └─ Redis SETNX idempotency:lock:{key} (10초)로 동시 진입 제어
                               │
                               ├─ 3. try {
                               │     │
                               │     ├─ [재고 차감] RedisStockManager.reserveStock()
                               │     │     └─ Redis Lua Script 실행 (실시간 재고 원자적 DECR)
                               │     │
                               │     ├─ [유효성 검증] 가격 검증 및 사용자 잔액 사전 검증
                               │     │
                               │     ├─ [결제 처리] PaymentProcessor.process() (트랜잭션 격리)
                               │     │     ├─ 우선순위 정렬: internal(Y_POINT) 우선 선행, external(CREDIT_CARD) 후행
                               │     │     ├─ Y_POINT: 포인트 차감 트랜잭션 수행 (point_transactions USE행 생성)
                               │     │     └─ CREDIT_CARD: PG사 승인 API 호출 (동기 외부 I/O)
                               │     │
                               │     └─ [데이터 영속화] BookingPersistenceService.persistBookingAndPayments()
                               │           └─ 단일 DB 트랜잭션 (@Transactional):
                               │              - bookings.saveAndFlush() (3차 멱등 - 물리 제약 충돌 감지)
                               │              - payments & histories 저장
                               │              - 선행 처리된 포인트 거래 내역에 생성된 bookingId 매핑 후행 업데이트
                               │
                               ├─ } finally {
                               │     IdempotencyLockManager.release() (Redis 분산 락 해제)
                               │   }
                               │
                               └─ return BookingResponse
```

---

### 3. 예약 실패 및 Saga 보상 흐름 (카드 거절 등 예외 시)
복합 결제 도중 외부 채널 결제가 거절되거나 시스템 예외가 발생할 경우, 이미 수행된 포인트 차감 내역 및 실시간 재고 차감 내역을 안전하게 일관된 상태로 원복하는 Saga 보상 메커니즘이 수행됩니다.

```
Client ──► BookingController ──► BookingService
                                        │
                                        ├─ 1. [멱등 1차/2차] 검증 성공 통과
                                        │
                                        ├─ 2. try {
                                        │     │
                                        │     ├─ [재고 차감] Redis Lua Script 성공
                                        │     │
                                        │     ├─ [결제 처리] PaymentProcessor.process()
                                        │     │     ├─ Y_POINT 결제 승인 성공 (포인트 1차 차감됨)
                                        │     │     │
                                        │     │     └─ CREDIT_CARD 승인 요청 -> PG사 승인 거절 ("한도초과")
                                        │     │
                                        │     ├─ [Saga 보상 결제] compensateInReverseOrder()
                                        │     │     └─ 성공했던 Y_POINT 승인 건을 역순 환불 처리
                                        │     │        └─ PointBalanceAdapter.restore() 호출
                                        │     │           - restore 멱등성 체크 (중복 환불 방지)
                                        │     │           - 포인트 잔액 복구 및 point_transactions RESTORE행 생성
                                        │     │
                                        │     └─ throw PaymentFailedException
                                        │
                                        ├─ } catch (PaymentFailedException e) {
                                        │     │
                                        │     └─ [Saga 보상 재고] compensate()
                                        │           └─ RedisStockManager.rollbackStock() -> Redis INCR로 재고 원복
                                        │
                                        ├─ } finally {
                                        │     IdempotencyLockManager.release() (Redis 락 해제)
                                        │   }
                                        ▼
                                GlobalExceptionHandler (에러 변환 후 402 반환)
```

---

### 4. 멱등성 (Idempotency) 3-tier 방어선 흐름
동작 시 순간적인 네트워크 타임아웃이나 사용자의 더블 클릭("따닥")으로 인해 중복된 결제 시도가 발생하더라도, 단 한 번의 정상적인 처리만을 보장하도록 설계된 철저한 3단계 방어 흐름입니다.

```
첫 번째 요청 (선행 도착)                    두 번째 요청 (동시 진입 시도)
        │                                          │
        ▼                                          ▼
BookingService.createBooking()              BookingService.createBooking()
        │                                          │
    [멱등 1차]                                  [멱등 1차]
 bookings 테이블 내 키 조회                    bookings 테이블 내 키 조회
   - 조회 결과: 없음 (통과)                     - 조회 결과: 없음 (통과)
        │                                          │
    [멱등 2차]                                  [멱등 2차]
 Redis SETNX 락 획득 시도                      Redis SETNX 락 획득 시도
   - 결과: 성공 (Lock 점유)                     - 결과: 실패 (Lock 획득 실패)
        │                                          │
    [비즈니스 로직 진행]                 IdempotencyConflictException (409 에러)
 재고차감, 외부 결제 및 DB 저장             
        │
    [멱등 3차 - 극단적 동시 발생 시]
 데이터베이스 saveAndFlush() 시점에
 고유 인덱스(idempotency_key) 충돌 감지
   - 충돌 시 DataIntegrityViolationException
   - DuplicateBookingException (409 에러)로 복구
```

---

## 5. 데이터 모델 및 인덱스 전략 (ERD)

본 시스템은 서비스 성능 극대화와 향후 마이크로서비스 아키텍처(MSA)로의 전환 가능성을 고려하여 설계되었습니다. 따라서 테이블 간의 **물리적 외래 키(FK) 제약조건은 제거**하였으며, 무결성은 애플리케이션의 트랜잭션 경계 안에서 제어합니다. 데이터베이스 레벨에서는 단지 논리적인 참조 관계만 유지하고, 조회 속도 성능을 위한 인덱스 튜닝을 철저히 수행하고 있습니다.

### 데이터 모델 구조 개요 (Logical Diagram)
```
┌──────────────┐     ┌──────────────┐
│  room_types  │ 1:N │   products   │
│    (객실)     │ ◄── │  (날짜별상품)   │
└──────────────┘     └──────┬───────┘
                            │ (논리 참조, FK 없음)
                            ▼
                     ┌──────────────┐
                     │   bookings   │
                     │  (최종 예약)   │
                     └──────┬───────┘
                            │ 1:N (논리 참조)
            ┌───────────────┴───────────────┐
            ▼                               ▼
     ┌──────────────┐              ┌────────────────────────┐
     │   payments   │              │   point_transactions   │
     │  (결제 수단)   │              │    (포인트 거래 이력)      │
     └──────┬───────┘              └────────────────────────┘
            │ 1:N (논리 참조)
            ▼
     ┌──────────────────┐
     │ payment_histories│
     │  (결제 상태 변천)    │
     └──────────────────┘
```

---

### 테이블 명세 및 전략

#### 1. `room_types` (객실 메타 정보)
객실의 이름, 정가 가격, 표준 체크인/체크아웃 시간 등의 메타 데이터를 담고 있는 거의 변하지 않는 정적 테이블입니다.
- **인덱스**: `id` (PK)

#### 2. `products` (날짜별 한정 판매 상품 정보)
특정 일자에 판매되는 상품을 정의하며, 본 시스템에서는 **일반 판매 상품과 이벤트성 한정 특가 상품을 별도의 엔티티로 나누지 않고 `open_at` 속성을 통해 단일 구조로 통합**하여 다형성을 보장합니다.
- **실시간 재고 전략**: 데이터베이스의 `total_stock` 컬럼은 초기 등록 시점의 재고입니다. 대규모 동시성 아래에서의 정합성을 보장하기 위해 **실시간 재고의 원천(Source of Truth)은 Redis의 `stock:product:{id}` 키**로 관리합니다.
- **인덱스**:
  - `id` (PK)
  - `UNIQUE (room_type_id, stay_date)`: 동일 객실 타입에 대해 중복 상품이 생성되는 것을 원천 차단합니다.
  - `INDEX (open_at)`: 판매 시간대별 상품 조회를 최적화합니다.

#### 3. `bookings` (예약 정보)
결제가 모두 정상 처리된 이후 최종적으로 데이터베이스에 생성되는 예약 정보입니다.
- **멱등성 보장**: `idempotency_key` 속성에 UNIQUE 인덱스를 설정하여 3차 방어선 역할을 하도록 설계되었습니다.
- **인덱스**:
  - `id` (PK)
  - `UNIQUE (booking_number)`: 비즈니스 채번 고유성 보장
  - `UNIQUE (idempotency_key)`: 멱등 요청의 물리적 중복 차단
  - `INDEX (user_id)`, `INDEX (product_id)`: 사용자별/상품별 이력 조회 최적화

#### 4. `payments` (결제 정보)
하나의 예약 건에 속한 개별 결제 수단별 세부 내역을 저장하는 테이블입니다. (예: 15.9만 원 결제 시 카드 결제행 1건 + 포인트 결제행 1건)
- **인덱스**:
  - `id` (PK)
  - `INDEX (booking_id)`: 예약 정보 기반의 결제 내역 조회 성능 보존
  - `INDEX (payment_type, status)`: 정산 조회 최적화

#### 5. `payment_histories` (결제 상태 이력 정보)
각 결제 건의 상태 변화(예: 시도 -> 성공 또는 실패 -> 환불 등)를 기록하는 감사(Audit) 및 추적 목적의 로그성 테이블입니다.
- **인덱스**:
  - `id` (PK)
  - `INDEX (payment_id)`: 결제 식별자 기반의 이력 추적 최적화
  - `INDEX (step, created_at)`: 거래 시간순 정렬 및 시각화 정렬

#### 6. `user_wallets` (사용자 지갑 정보)
사용자별 포인트 잔액 정보를 보관하는 테이블입니다.
- **동시성 최적화**: 포인트 차감 과정에서 다수의 사용자가 각자 본인의 지갑을 제어하므로 경합이 분산됩니다. 따라서 데이터베이스의 무거운 행 잠금(Row Lock)을 피하기 위해 **낙관적 락(`@Version`)**을 적용하여 성능 손실 없이 안정성을 확보했습니다.
- **인덱스**: `user_id` (PK)

#### 7. `point_transactions` (포인트 거래 이력)
포인트의 차감(`USE`) 및 원복(`RESTORE`) 거래 상세 내역을 투명하게 보관하는 원장 테이블입니다.
- **이력 추적 용이성**: `balance_before`와 `balance_after` 컬럼을 명시적으로 포함하여, 사고 발생 시 시간순으로 거래를 추적하여 잔액을 검증할 수 있는 회계적 무결성을 제공합니다.
- **1:1 짝 연동**: 환불 거래(`RESTORE`)가 발생할 때, 기 거래된 `original_transaction_id`(차감 거래 PK)를 명시적으로 보관하고 중복 환불을 확인하여 멱등 환불을 보장합니다.
- **인덱스**:
  - `id` (PK)
  - `INDEX (user_id, created_at)`: 사용자의 포인트 이용 내역 타임라인 조회 최적화
  - `INDEX (booking_id)`: 예약 정보 연관 조회용

---

### Redis 키 스페이스 설계

애플리케이션 외부에서 빠르고 가볍게 동시성을 통제하기 위해 Redis에 아래와 같은 구조화된 키 세트를 구성하고 있습니다.

| Redis 키 패턴 | 데이터 타입 | 기본 TTL | 목적 |
| :--- | :--- | :--- | :--- |
| `stock:product:{id}` | String | 없음 (영구) | **실시간 재고 소스**. 분산 인스턴스 전반의 실시간 예약 가능 재고를 관리합니다. |
| `checkout:product:{id}` | JSON String | 5초 | Checkout API의 무거운 상품/객실 상세 조회를 위한 Look-aside 캐시입니다. |
| `idempotency:lock:{key}` | String | 10초 | 예약 API의 다중 동시 진입(따닥)을 차단하기 위한 분산 락 키입니다. |
| `lock:data_seed` | String | 10초 | 시스템 기동 시 여러 애플리케이션 중 1개 인스턴스만 시딩을 하도록 통제하는 분산 락입니다. |

---

## 6. 성능 및 정합성 검증 결과 (k6 기반)

플랫폼의 신뢰성을 실증적으로 검증하기 위해 k6 부하 테스트 도구를 활용하여 다양한 트래픽 시나리오 테스트를 수행하였습니다. 모든 수치와 결과는 실제 다중 애플리케이션 인스턴스 환경에서 계측된 신뢰할 수 있는 데이터입니다. \
각 개선 단계의 구체적인 k6 원본 JSON 메트릭과 상세 기술 분석 리포트는 `/load-test/results` 에서 확인하실 수 있습니다.

### 1. 정합성 및 무결성 검증 결과

대규모 병렬 동시 요청이 집중되는 상황 속에서 데이터가 오염되지 않고 일관성을 유지하는지에 대한 실증 결과입니다.

| 검증 시나리오 | 검증 목적 | 계측 결과 |
| :--- | :--- | :--- |
| **초과 판매 제어** (동시 100요청 / 재고 10개) | 한정 재고 수량을 넘어서는 불공정 초과 판매 차단 여부 | `bookings` 생성 개수: **정확히 10건**<br>초과 예약율: **0.00%** |
| **멱등 요청 필터링** (동일 멱등키 300회 중복 시도) | 네트워크 재시도에 따른 이중 결제 방지 여부 | `bookings` 생성 개수: **정확히 3건** (중복 없는 3명의 사용자)<br>p95 응답 속도: **4.9ms** (대부분 1차 인메모리 필터링 통과) |
| **Saga 보상 트랜잭션** (결제 대행사 승인 실패 10건) | 외부 요인 실패 시 차감되었던 재고 및 포인트의 자가 원복 여부 | `bookings` 생성 개수: **0건** (전원 실패 처리)<br>Redis `stock:product:1` 재고: **10개 원복**<br>사용자 잔액: **100% 정상 원복**<br>`point_transactions`: **차감 10건 / 원복 10건 쌍 기록** |

---

### 2. 주문서 캐싱(Look-aside) 도입 효과 비교

가장 무거운 단일 상품 메타 정보 조회 병목(핫스팟)을 극복하기 위해 1차적으로 Redis 캐싱 전략을 도입하였으며, 도입 전과 후의 성능 개선 편차를 단일 인스턴스 상에서 k6로 검증한 결과입니다.

| 성능 계측 항목 | 캐시 미적용 (Direct DB) | 캐시 적용 (Redis Look-aside) | 개선율 |
| :--- | :--- | :--- | :--- |
| **p95 레이턴시** | 208 ms | **5.6 ms** | **97.3% 단축** |
| **낙오 요청 수 (dropped)** | 575 건 | **0 건** | **100% 해소** |

---

### 3. 단일 인스턴스 대비 수평 확장(분산 아키텍처)의 효과 (2단계: 피크 지속 부하 극복)

일차적으로 캐싱을 적용했음에도 불구하고, 단일 애플리케이션 인스턴스의 쓰레드 풀 및 CPU 자원 한계로 인해 1,000 TPS 피크 트래픽이 지속적으로 유입(Peak)되거나 순간 폭증(Spike)할 때 발생하는 지연 현상을 `nginx` 라운드 로빈 로드 밸런서와 다중 인스턴스(`app1` + `app2`) 분산 수평 확장(Scale-out) 아키텍처로 최종 극복한 실증 지표입니다.

#### 시나리오 A: 주문서 조회 (Checkout API Peak 1,000 TPS 지속 테스트)
- **단일 인스턴스 (캐시 적용)**: 피크 누적으로 인한 지연 발생 (p95 레이턴시 **2,758ms** / dropped 건수 **5,369건**)
- **분산 수평 확장 (`app1` + `app2`)**: 부하 분산 및 신속 처리 (p95 레이턴시 **3.3ms** / dropped 건수 **0건** - 성능 고도화 완료)

#### 시나리오 B: 예약 생성 (Booking API Spike 트래픽 순간 1,000 TPS 유입 테스트)
- **단일 인스턴스 (인프라 한계)**: p95 레이턴시 **5,213ms** / dropped 건수 **11,487건**
- **분산 수평 확장 (`app1` + `app2`)**: p95 레이턴시 **22.8ms** / dropped 건수 **18건** (지연 시간 99.6% 개선)

---

## 7. 디렉토리 구조 및 역할

도메인 기반 패키지 설계를 채택하여 각 컴포넌트의 가독성과 책임 분리를 지향하였습니다.

```
src/main/java/.../bookingpayment/
├── booking/                # 예약 생성 핵심 처리 도메인
│   ├── api/                # BookingController (진입점)
│   ├── domain/             # Booking 엔티티 및 상태값
│   ├── dto/                # 요청/응답 변환 데이터 객체
│   ├── repository/         # BookingRepository (1차 멱등 검사 포함)
│   └── service/            # 복합 처리 오케스트레이터 및 분산 컴포넌트들
│       ├── BookingService             # 오케스트레이션 및 Saga 보상 흐름 제어
│       ├── BookingPersistenceService  # 단일 데이터베이스 트랜잭션 처리 (@Transactional)
│       ├── IdempotencyLockManager     # Redis 멱등 락 획득/해제 관리
│       ├── RedisStockManager          # Redis Lua 스크립트 기반 실시간 재고 통제
│       └── PaymentCommandMapper       # 외부/내부 결제 명령어 구조화
│
├── checkout/               # 주문서 진입 패키지
│   ├── api/                # CheckoutController
│   ├── cache/              # CachedProductInfoCacheRepository (Redis 연동)
│   ├── dto/                # 캐시 및 응답용 정보 묶음
│   └── service/            # CheckoutService 및 캐싱 레이어 분리
│
├── payment/                # 결제 추상화 및 다형성 제어 패키지
│   ├── config/             # Composite 결제 프로세서 등록 설정
│   ├── domain/             # Payment, PaymentHistory 엔티티 및 상태값
│   ├── infra/              # 외부 카드 대행사 연동(MockPgClient), 내부 포인트 연동 어댑터
│   ├── model/              # sealed 인터페이스 구조화 결제 상세 모델들
│   ├── port/               # 의존성 역전을 위한 PointBalancePort, PgClient 포트 정의
│   └── service/            # 다형성 결제 전략(Strategy Pattern) 및 보상 로직 수행
│
├── point/                  # 포인트 원장 및 회계 추적 이력 처리 패키지
├── user/                   # 사용자 지갑 및 낙관적 락 처리 패키지
├── product/                # 상품 도메인 및 기동 시 데이터 초기화 패키지
├── roomtype/               # 객실 타입 메타 도메인 패키지
├── common/                 # 공통 예외 핸들러 및 감사 데이터 정의 패키지
└── config/                 # Redis 인프라 및 JPA 감사 설정 패키지
```