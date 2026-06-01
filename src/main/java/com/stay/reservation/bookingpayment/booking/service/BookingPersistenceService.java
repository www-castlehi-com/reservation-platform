package com.stay.reservation.bookingpayment.booking.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.booking.domain.Booking;
import com.stay.reservation.bookingpayment.booking.domain.BookingStatus;
import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.repository.BookingRepository;
import com.stay.reservation.bookingpayment.common.exception.DuplicateBookingException;
import com.stay.reservation.bookingpayment.payment.domain.Payment;
import com.stay.reservation.bookingpayment.payment.domain.PaymentHistory;
import com.stay.reservation.bookingpayment.payment.domain.PaymentStatus;
import com.stay.reservation.bookingpayment.payment.domain.PaymentStep;
import com.stay.reservation.bookingpayment.payment.model.CompositePaymentResult;
import com.stay.reservation.bookingpayment.payment.model.PaymentResult;
import com.stay.reservation.bookingpayment.payment.repository.PaymentHistoryRepository;
import com.stay.reservation.bookingpayment.payment.repository.PaymentRepository;
import com.stay.reservation.bookingpayment.product.domain.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingPersistenceService {

	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository paymentHistoryRepository;

	@Transactional
	public Booking persistBookingAndPayments(BookingRequest request, Long userId, String idempotencyKey,
		Product product, CompositePaymentResult paymentResult) {
		Booking booking = Booking.builder()
			.bookingNumber(generateBookingNumber())
			.idempotencyKey(idempotencyKey)
			.userId(userId)
			.productId(product.getId())
			.totalAmount(request.payment().totalAmount())
			.status(BookingStatus.CONFIRMED)
			.customerName(request.customerName())
			.customerPhone(request.customerPhone())
			.build();

		Booking savedBooking;
		try {
			savedBooking = bookingRepository.saveAndFlush(booking);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateBookingException(idempotencyKey);
		}

		for (PaymentResult result : paymentResult.paymentResults()) {
			Payment payment = Payment.builder()
				.bookingId(savedBooking.getId())
				.paymentType(result.paymentType())
				.amount(result.amount())
				.transactionId(result.transactionId())
				.status(PaymentStatus.SUCCESS)
				.build();
			Payment savedPayment = paymentRepository.save(payment);

			PaymentHistory history = PaymentHistory.builder()
				.paymentId(savedPayment.getId())
				.step(PaymentStep.CHARGE_SUCCESS)
				.amount(result.amount())
				.transactionId(result.transactionId())
				.build();
			paymentHistoryRepository.save(history);
		}

		return savedBooking;
	}

	private String generateBookingNumber() {
		return "B" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + ThreadLocalRandom.current()
			.nextInt(10000, 99999);
	}
}
