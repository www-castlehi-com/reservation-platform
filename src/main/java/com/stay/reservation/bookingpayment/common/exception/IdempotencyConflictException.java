package com.stay.reservation.bookingpayment.common.exception;

public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String idempotencyKey) {
		super("현재 처리 중인 예약 요청입니다. 잠시 후 다시 시도해 주세요. (Idempotency Key: " + idempotencyKey + ")");
	}
}
