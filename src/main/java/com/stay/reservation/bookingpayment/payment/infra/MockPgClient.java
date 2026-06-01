package com.stay.reservation.bookingpayment.payment.infra;

import java.util.concurrent.atomic.AtomicLong;

import com.stay.reservation.bookingpayment.payment.port.pg.PgAuthorizeRequest;
import com.stay.reservation.bookingpayment.payment.port.pg.PgClient;
import com.stay.reservation.bookingpayment.payment.port.pg.PgResponse;

public class MockPgClient implements PgClient {

	private static final long CARD_LIMIT_THRESHOLD = 1_000_000L;

	private final String pgName;
	private final AtomicLong transactionSequence = new AtomicLong(1);

	public MockPgClient(String pgName) {
		this.pgName = pgName;
	}

	@Override
	public PgResponse authorize(PgAuthorizeRequest pgAuthorizeRequest) {
		String token = pgAuthorizeRequest.paymentToken();

		// 1. 토큰 기반 결제 실패 시나리오 모킹 (E2E/k6 부하 테스트 및 시나리오 테스트 연동용)
		if ("tok_decline_limit_exceeded".equals(token)) {
			return PgResponse.declined("LIMIT_EXCEEDED", "결제 한도를 초과했습니다. (모의 한도 초과 토큰)");
		}

		if ("tok_decline_insufficient_funds".equals(token)) {
			return PgResponse.declined("INSUFFICIENT_FUNDS", "잔액이 부족합니다. (모의 잔액 부족 토큰)");
		}

		if ("tok_timeout".equals(token)) {
			try {
				// PG 통신 타임아웃 지연 모사 (3초 대기)
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return PgResponse.declined("TIMEOUT", "결제 대행사 응답 시간 초과 (모의 타임아웃 토큰)");
		}

		// 2. 기본 임계치 기반 결제 실패
		if (pgAuthorizeRequest.amount() > CARD_LIMIT_THRESHOLD) {
			return PgResponse.declined("LIMIT_EXCEEDED", "결제 한도를 초과했습니다. 요청 금액=" + pgAuthorizeRequest.amount());
		}

		String pgTransactionId = generateTransactionId();
		return PgResponse.approved(pgTransactionId);
	}

	@Override
	public PgResponse cancel(String pgTransactionId, long amount) {
		String cancelTransactionId = generateTransactionId();
		return PgResponse.approved(cancelTransactionId);
	}

	private String generateTransactionId() {
		return "TX-" + pgName + "-" + transactionSequence.getAndIncrement();
	}
}
