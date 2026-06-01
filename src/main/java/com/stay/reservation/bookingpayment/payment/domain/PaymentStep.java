package com.stay.reservation.bookingpayment.payment.domain;

public enum PaymentStep {
	CHARGE_ATTEMPTED, CHARGE_SUCCESS, CHARGE_FAILED, REFUND_ATTEMPTED, REFUND_SUCCESS, REFUND_FAILED, ROLLBACK_FAILED
}
