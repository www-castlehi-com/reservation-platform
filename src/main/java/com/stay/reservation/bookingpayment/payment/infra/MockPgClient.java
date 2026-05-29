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
