package com.stay.reservation.bookingpayment.payment.exception;

import com.stay.reservation.bookingpayment.payment.model.CompositePaymentResult;

import lombok.Getter;

@Getter
public class PaymentFailedException extends RuntimeException {

	private final CompositePaymentResult result;

	public PaymentFailedException(CompositePaymentResult result) {
		super("Payment failed. compensationCompleted=" + result.compensationCompleted());
		this.result = result;
	}
}
