package com.stay.reservation.bookingpayment.payment.port.pg;

public record PgResponse(boolean approved, String pgTransactionId, String failureCode, String failureMessage) {

	public static PgResponse approved(String pgTransactionId) {
		return new PgResponse(true, pgTransactionId, null, null);
	}

	public static PgResponse declined(String failureCode, String failureMessage) {
		return new PgResponse(false, null, failureCode, failureMessage);
	}
}
