package com.stay.reservation.bookingpayment.booking.service;

import org.springframework.stereotype.Component;

import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingFacade {

	private final BookingService bookingService;

	public BookingResponse createBooking(Long userId, String idempotencyKey, BookingRequest request) {
		log.info("Initiating booking flow via Facade for user: {}, product: {}", userId, request.productId());
		return bookingService.createBooking(request, userId, idempotencyKey);
	}
}

