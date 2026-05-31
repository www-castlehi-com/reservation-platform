package com.stay.reservation.bookingpayment.common.exception;

public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(Long productId) {
		super("해당 상품을 찾을 수 없습니다. (ID: " + productId + ")");
	}
}
