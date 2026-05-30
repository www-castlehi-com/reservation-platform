package com.stay.reservation.bookingpayment.payment.exception;

/**
 * 허용되지 않는 결제 수단 조합이 요청됐을 때 발생한다.
 * 예: 신용카드 + Y페이 혼용, 동일 수단 중복, 3개 이상 수단 사용.
 */
public class InvalidPaymentCombinationException extends RuntimeException {

	public InvalidPaymentCombinationException(String message) {
		super(message);
	}
}
