package com.stay.reservation.bookingpayment.idempotency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stay.reservation.bookingpayment.idempotency.domain.BookingIdempotency;

@Repository
public interface BookingIdempotencyRepository extends JpaRepository<BookingIdempotency, String> {

}
