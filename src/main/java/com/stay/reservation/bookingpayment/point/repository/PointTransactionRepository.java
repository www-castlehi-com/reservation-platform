package com.stay.reservation.bookingpayment.point.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stay.reservation.bookingpayment.point.domain.PointTransaction;
import com.stay.reservation.bookingpayment.point.domain.PointTransactionType;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

	boolean existsByOriginalTransactionIdAndType(Long originalTransactionId, PointTransactionType type);
}
