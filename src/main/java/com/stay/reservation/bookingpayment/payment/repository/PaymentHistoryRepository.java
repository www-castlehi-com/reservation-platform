package com.stay.reservation.bookingpayment.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stay.reservation.bookingpayment.payment.domain.PaymentHistory;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

	List<PaymentHistory> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}
