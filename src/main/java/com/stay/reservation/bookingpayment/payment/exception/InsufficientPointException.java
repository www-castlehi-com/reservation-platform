package com.stay.reservation.bookingpayment.payment.exception;

/**
 * 포인트 잔액이 결제 요청 금액보다 부족할 때 발생한다.
 */
public class InsufficientPointException extends RuntimeException {

	public InsufficientPointException(String message) {
		super(message);
	}
}
