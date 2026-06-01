package com.stay.reservation.bookingpayment.payment.model;

public record PaymentResult(boolean success, PaymentType paymentType, long amount, String transactionId,
							String failureReason) {

	public static PaymentResult success(PaymentType paymentType, long amount, String transactionId) {
		return new PaymentResult(true, paymentType, amount, transactionId, null);
	}

	public static PaymentResult failure(PaymentType paymentType, long amount, String failureReason) {
		return new PaymentResult(false, paymentType, amount, null, failureReason);
	}

	public boolean isFailure() {
		return !success;
	}
}
