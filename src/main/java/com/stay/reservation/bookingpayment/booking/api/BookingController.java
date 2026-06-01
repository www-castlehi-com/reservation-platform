package com.stay.reservation.bookingpayment.booking.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.booking.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

	private final BookingService bookingService;

	@PostMapping
	public ResponseEntity<BookingResponse> createBooking(@RequestHeader("X-User-Id") Long userId,
		@RequestHeader("X-Idempotency-Key") String idempotencyKey, @Valid @RequestBody BookingRequest request) {
		log.info("Received booking request. User: {}, IdempotencyKey: {}", userId, idempotencyKey);
		BookingResponse response = bookingService.createBooking(request, userId, idempotencyKey);
		return ResponseEntity.ok(response);
	}
}
