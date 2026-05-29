package com.stay.reservation.bookingpayment.payment.exception;

/**
 * 결제 수단별 금액의 합이 총 결제 금액과 일치하지 않을 때 발생한다.
 */
public class PaymentAmountMismatchException extends RuntimeException {

	public PaymentAmountMismatchException(String message) {
		super(message);
	}
}
