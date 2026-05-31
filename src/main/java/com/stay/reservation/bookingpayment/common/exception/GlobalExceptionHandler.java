package com.stay.reservation.bookingpayment.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(ProductNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException exception) {
		log.warn("Product not found: {}", exception.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ErrorResponse("PRODUCT_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleInvalidUser(UserNotFoundException exception) {
		log.warn("Invalid user: {}", exception.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ErrorResponse("INVALID_USER", exception.getMessage()));
	}

	@ExceptionHandler(SoldOutException.class)
	public ResponseEntity<ErrorResponse> handleSoldOut(SoldOutException exception) {
		log.warn("Sold out: {}", exception.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("SOLD_OUT", exception.getMessage()));
	}

	@ExceptionHandler(DuplicateBookingException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateBooking(DuplicateBookingException exception) {
		log.warn("Duplicate booking: {}", exception.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ErrorResponse("DUPLICATE_BOOKING", exception.getMessage()));
	}

	@ExceptionHandler(PriceMismatchException.class)
	public ResponseEntity<ErrorResponse> handlePriceMismatch(PriceMismatchException exception) {
		log.warn("Price mismatch: {}", exception.getMessage());
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
			.body(new ErrorResponse("PRICE_MISMATCH", exception.getMessage()));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
		if ("X-User-Id".equals(exception.getHeaderName())) {
			log.warn("Missing required header X-User-Id");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("INVALID_USER", "X-User-Id 헤더가 누락되었습니다."));
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGenericException(Exception exception) {
		log.error("Unexpected error occurred", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
	}

	public record ErrorResponse(String errorCode, String message) {

	}
}
