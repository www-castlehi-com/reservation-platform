package com.stay.reservation.bookingpayment.payment.model;

import java.util.Collections;
import java.util.List;

public record CompositePaymentResult(boolean success, List<PaymentResult> paymentResults, PaymentType failedPaymentType,
									 String failureReason, boolean compensationCompleted) {

	public CompositePaymentResult {
		paymentResults = paymentResults == null ? Collections.emptyList() : List.copyOf(paymentResults);
	}

	public static CompositePaymentResult success(List<PaymentResult> paymentResults) {
		return new CompositePaymentResult(true, paymentResults, null, null, true);
	}

	public static CompositePaymentResult failure(PaymentType failedPaymentType, String failureReason,
		boolean compensationCompleted) {
		return new CompositePaymentResult(false, Collections.emptyList(), failedPaymentType, failureReason,
			compensationCompleted);
	}

	public boolean isAllSuccess() {
		return success;
	}
}
