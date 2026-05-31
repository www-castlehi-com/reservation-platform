package com.stay.reservation.bookingpayment.common.exception;

public class SoldOutException extends RuntimeException {

	public SoldOutException(Long productId) {
		super("재고가 소진되었습니다. (ID: " + productId + ")");
	}
}
