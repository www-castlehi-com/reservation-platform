package com.stay.reservation.bookingpayment.common.exception;

public class DuplicateBookingException extends RuntimeException {

	public DuplicateBookingException(String idempotencyKey) {
		super("이미 완료된 예약 요청입니다. (Idempotency Key: " + idempotencyKey + ")");
	}
}
