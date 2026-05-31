# Load Test

## 실행 방법

```bash
docker compose up
# booking-payment 실행 후
k6 run load-test/{test}.js
```

## 스크립트 설명

| 파일 | 설명 |
|------|------|
| `checkout-test.js` | 200 TPS의 부하 조건 하에서 단일 상품 특가 예약 진입 화면(Checkout) 성능 측정 |

## results
측정 결과 요약을 두는 곳.
