package com.stay.reservation.bookingpayment.common.exception;

public class PriceMismatchException extends RuntimeException {

	public PriceMismatchException() {
		super("상품 가격이 일치하지 않습니다.");
	}
}
