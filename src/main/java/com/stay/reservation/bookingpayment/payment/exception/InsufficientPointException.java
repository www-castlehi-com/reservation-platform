package com.stay.reservation.bookingpayment.payment.exception;

import lombok.Getter;

@Getter
public class InsufficientPointException extends RuntimeException {

	private final long userId;
	private final long currentBalance;
	private final long requestedAmount;

	public InsufficientPointException(long userId, long currentBalance, long requestedAmount) {
		super(String.format("User %d has insufficient points. Current: %d, Requested: %d", userId, currentBalance,
			requestedAmount));
		this.userId = userId;
		this.currentBalance = currentBalance;
		this.requestedAmount = requestedAmount;
	}
}
