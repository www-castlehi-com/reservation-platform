package com.stay.reservation.bookingpayment.common.exception;

public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(Long userId) {
		super("올바르지 않거나 존재하지 않는 사용자입니다. (ID: " + userId + ")");
	}
}
