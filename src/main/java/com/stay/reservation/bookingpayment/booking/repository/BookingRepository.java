package com.stay.reservation.bookingpayment.booking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stay.reservation.bookingpayment.booking.domain.Booking;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

	Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
