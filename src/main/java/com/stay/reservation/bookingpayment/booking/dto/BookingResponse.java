package com.stay.reservation.bookingpayment.booking.dto;

import java.time.LocalDateTime;

import com.stay.reservation.bookingpayment.booking.domain.Booking;
import com.stay.reservation.bookingpayment.booking.domain.BookingStatus;

public record BookingResponse(Long bookingId, String bookingNumber, BookingStatus status, Long totalAmount,
							  LocalDateTime createdAt) {

	public static BookingResponse from(Booking booking) {
		return new BookingResponse(booking.getId(), booking.getBookingNumber(), booking.getStatus(),
			booking.getTotalAmount(), booking.getCreatedAt());
	}
}
