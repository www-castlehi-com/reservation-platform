package com.stay.reservation.bookingpayment.payment.model;

/**
 * 단일 결제 수단의 결제 결과.
 *
 * <p>[Gemini 피드백 ① 반영] 환불(보상) 시 금액 정보가 반드시 필요하므로 {@code amount}를 포함한다.
 * 또한 {@link com.stay.reservation.bookingpayment.payment.service.PaymentProcessor}가 보상 시 어떤 수단으로 환불할지 결정하기 위해
 * {@code paymentType}도 함께 담는다.
 *
 * @param success         결제 성공 여부.
 * @param paymentType     결제 수단 종류(보상 시 어떤 PaymentMethod로 환불할지 결정).
 * @param amount          결제(또는 환불 대상) 금액. 보상 트랜잭션에 필수.
 * @param transactionId   거래 식별자. 외부 PG 거래번호 또는 내부 거래번호.
 *                        환불 시 이 ID로 원거래를 취소한다. 실패 시 null일 수 있다.
 * @param failureReason   실패 사유. 성공 시 null.
 */
public record PaymentResult(boolean success, PaymentType paymentType, long amount, String transactionId,
							String failureReason) {

	/**
	 * 결제 성공 결과를 생성한다.
	 */
	public static PaymentResult success(PaymentType paymentType, long amount, String transactionId) {
		return new PaymentResult(true, paymentType, amount, transactionId, null);
	}

	/**
	 * 결제 실패 결과를 생성한다.
	 */
	public static PaymentResult failure(PaymentType paymentType, long amount, String failureReason) {
		return new PaymentResult(false, paymentType, amount, null, failureReason);
	}

	public boolean isFailure() {
		return !success;
	}
}
