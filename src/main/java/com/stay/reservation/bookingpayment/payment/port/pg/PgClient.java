package com.stay.reservation.bookingpayment.payment.port.pg;

public interface PgClient {

	PgResponse authorize(PgAuthorizeRequest pgAuthorizeRequest);

	PgResponse cancel(String pgTransactionId, long amount);
}
