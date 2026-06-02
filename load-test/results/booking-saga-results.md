## 1. 테스트 환경

- **구성**: app1 + app2 + nginx + MySQL + Redis (분산 환경)
- **시나리오**: 10명의 사용자가 동시에 카드 결제 시도, 카드 토큰을 `tok_decline_limit_exceeded`로 설정해 PG에서 거절 유도
- **결제 구성**: 복합 결제 (CREDIT_CARD 149,000원 + Y_POINT 10,000원 = 159,000원)

## 2. 측정 결과

### k6 메트릭
| 항목 | 값 |
|---|---|
| 총 요청 | 10건 |
| 모든 요청 HTTP 상태 | 402 (Payment Required) |
| 평균 latency | 684ms |
| p95 latency | 728ms |
| check `payment correctly declined` | 10/10 통과 |
| check `has error body` | 10/10 통과 |

### DB 검증 — Saga 보상이 정확히 동작

| 검증 항목 | 기대값 | 실측값 | 결과 |
|---|---|---|---|
| bookings 생성 수 | 0 | 0 | ✅ |
| Redis `stock:product:1` | 100 (원복) | 100 | ✅ |
| point_transactions (USE) | 10건 × 10,000원 | 10건 × 10,000원 | ✅ |
| point_transactions (RESTORE) | 10건 × 10,000원 | 10건 × 10,000원 | ✅ |
| user_wallets (1001~1010) | 모두 50,000 | 모두 50,000 | ✅ |

## 3. 시간 순서로 본 Saga LIFO 보상 흐름

로그 기준 (10:19:43.x):

```
.140~.156  10개 booking 요청 nginx 도착 → app1/app2에 분배 (5:5)
.344~.381  Y_POINT.charge() — PointBalanceAdapter.deduct() 호출
.365~.371  Y_POINT 차감 완료 (txId 1~5, balanceAfter=40000)
.396~.398  Y_POINT.refund() — PointBalanceAdapter.restore() 호출
             ↑ 카드 결제(CREDIT_CARD)가 LIMIT_EXCEEDED로 거절되어 역순 보상 시작
.429~.443  Y_POINT 복구 완료 (restoreTxId 6~10, balanceAfter=50000)
.448~.463  BookingService catch — PaymentFailedException, compensationCompleted=true
.459~.465  Redis stock released (재고 복구 로그)
.471       GlobalExceptionHandler → 402 응답
```

흐름 요약:
1. **PaymentProcessor의 orderByChannelPriority**: Y_POINT(internal) 먼저, CREDIT_CARD(external) 나중
2. Y_POINT 차감 성공 → succeededPaymentResults에 적재
3. CREDIT_CARD 호출 → MockPgClient가 `tok_decline_limit_exceeded` 감지 → declined 응답
4. **PaymentProcessor.compensateInReverseOrder()**: succeededPaymentResults 역순으로 refund 호출
5. Y_POINT.refund() → PointBalanceAdapter.restore() → user_wallets 복구
6. CompositePaymentResult.failure(compensationCompleted=true) 반환
7. BookingService에서 `PaymentFailedException` throw
8. BookingService catch: stockDeducted=true → RedisStockManager.rollbackStock()
9. finally: idempotencyLockManager.release()
10. 클라이언트에 402 응답

## 4. point_transactions 회계 검증

샘플 데이터 (수동 SQL 조회):
```
balance_after  balance_before  idempotency_key                       original_tx_id  type     user_id
40000          50000           a5a5952f-d20e-4aca-b1ad-9e77a7258e97  (null)           USE      1005
40000          50000           a9915092-06b1-47f5-b67d-6ac016c1aeb8  (null)           USE      1003
40000          50000           1daac727-7f41-452b-8ccb-7e93a3eb315f  (null)           USE      1008
40000          50000           20f7a598-1a11-42ba-bbf0-f9a8b1a278bd  (null)           USE      1004
40000          50000           6969de7e-d737-49a1-b0c7-3693f618e24e  (null)           USE      1006
50000          40000           (null)                                4                RESTORE  1004
50000          40000           (null)                                5                RESTORE  1006
50000          40000           (null)                                1                RESTORE  1005
50000          40000           (null)                                3                RESTORE  1008
50000          40000           (null)                                2                RESTORE  1003
```

**검증 포인트**:
- USE 거래 5건 (각 다른 사용자, 다른 idempotency_key)
- RESTORE 거래 5건 (각각 USE의 PK를 `original_transaction_id`로 참조)
- USE의 user_id == 해당 RESTORE의 user_id (1003, 1004, 1005, 1006, 1008 짝 확인)
- balance_before/after 흐름: 50000 → 40000 (USE) → 50000 (RESTORE)

## 5. 발견된 점

### ✅ 잘 동작한 것
- PaymentProcessor의 LIFO 역순 보상이 의도대로 작동
- 분산 환경(app1/app2)에서도 보상 흐름 정확히 실행
- 모든 보상이 멱등성 보장된 상태로 완료 (RESTORE 중복 호출 시 skip 로직)
- 모든 시간이 ms 단위 내에서 처리됨 (10개 booking 시도 → 보상 완료까지 약 100ms)

### ⚠ 정직하게 인정할 점
- **RESTORE의 idempotency_key 미사용**: USE에는 booking의 idempotency_key가 저장되지만, RESTORE에는 null. 멱등성은 `original_transaction_id` 체크로 보장되지만, 운영에선 USE의 idempotency_key를 RESTORE에 복사해두면 같은 booking 흐름의 USE/RESTORE 쌍을 한 번에 조회 가능. 향후 개선.
- **payments / payment_histories 미기록**: Saga 보상 흐름 전체가 메모리에서 진행되어 DB에 결제 시도 흔적이 안 남음 (Option A 구조). point_transactions의 USE/RESTORE 짝으로 회계 추적은 가능하지만, payments 테이블에 ROLLED_BACK 상태로 영속화하는 게 정석.
- **ROLLBACK_FAILED 영속화 안 됨**: 본 측정에선 모든 보상이 성공해서 발생 안 했지만, 실패 시 현재는 로그로만 기록. payment_histories에 ROLLBACK_FAILED 행 INSERT가 정석.