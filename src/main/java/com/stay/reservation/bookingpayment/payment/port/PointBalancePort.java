package com.stay.reservation.bookingpayment.payment.port;

public interface PointBalancePort {

	String deduct(long userId, long amount, String idempotencyKey);

	String restore(String pointTransactionId, long amount);
}
